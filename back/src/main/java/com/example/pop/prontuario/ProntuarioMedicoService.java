package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.configuracao.CustoIaService;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.storage.StorageService;
import com.example.pop.usoia.UsoIaService;
import com.example.pop.usoia.UsoIaTipo;

/** Monta o histórico do paciente (linha do tempo) e cuida do resumo do histórico por IA. */
@Service
public class ProntuarioMedicoService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Limite de atendimentos incluídos no texto enviado à IA (mantém o custo/contexto sob controle). */
    private static final int MAX_ATENDIMENTOS_RESUMO = 40;

    private final ProntuarioRepository prontuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final StorageService storageService;
    private final ConfiguracaoService configuracaoService;
    private final ResumoHistoricoService resumoHistoricoService;
    private final CustoIaService custoIaService;
    private final UsoIaService usoIaService;

    public ProntuarioMedicoService(ProntuarioRepository prontuarioRepository, PacienteRepository pacienteRepository,
            StorageService storageService, ConfiguracaoService configuracaoService,
            ResumoHistoricoService resumoHistoricoService, CustoIaService custoIaService,
            UsoIaService usoIaService) {
        this.prontuarioRepository = prontuarioRepository;
        this.pacienteRepository = pacienteRepository;
        this.storageService = storageService;
        this.configuracaoService = configuracaoService;
        this.resumoHistoricoService = resumoHistoricoService;
        this.custoIaService = custoIaService;
        this.usoIaService = usoIaService;
    }

    /** Cabeçalho do paciente + resumo do histórico + linha do tempo (prontuários, mais recente primeiro). */
    @Transactional(readOnly = true)
    public HistoricoMedicoResponse historico(Long pacienteId) {
        Paciente p = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado"));
        List<Prontuario> prontuarios = carregarProntuarios(pacienteId);

        long alertas = prontuarios.stream()
                .filter(pr -> pr.getStatusAlerta() == StatusAlertaProntuario.AGUARDANDO_VALIDACAO)
                .count();

        HistoricoMedicoResponse.PacienteCabecalho cab = new HistoricoMedicoResponse.PacienteCabecalho(
                p.getId(), p.getNome(), p.getCpf(), p.getDataNascimento(),
                p.getSexo() == null ? null : p.getSexo().name(),
                storageService.urlFotoPaciente(p.getFotoUrl()),
                p.getProntuario(),
                p.getTelefonesAdicionais() == null ? List.of() : List.copyOf(p.getTelefonesAdicionais()),
                p.getUnidades() == null ? List.of() : p.getUnidades().stream().map(u -> u.getNome()).toList(),
                alertas,
                prontuarios.size());

        List<ProntuarioAdminDetalheResponse> timeline = prontuarios.stream()
                .map(ProntuarioAdminDetalheResponse::from).toList();

        return new HistoricoMedicoResponse(cab, p.getResumoHistoricoIa(), p.getResumoHistoricoGeradoEm(),
                iaHabilitada(), resumoIaHabilitado(), timeline,
                p.getResumoHistoricoModeloIa(), p.getResumoHistoricoTokensEntrada(), p.getResumoHistoricoTokensSaida(),
                p.getResumoHistoricoCustoUsd(), p.getResumoHistoricoGeracoes());
    }

    /**
     * Regenera o resumo do histórico do paciente por IA e salva. Chamado automaticamente (assíncrono)
     * quando um documento é analisado. No-op se os toggles estiverem desligados, sem histórico, ou se
     * a IA falhar (mantém o resumo anterior — fail-safe). Faz a chamada lenta ao Claude fora de tx.
     */
    public void regenerarResumo(Long pacienteId) {
        if (pacienteId == null || !iaHabilitada() || !resumoIaHabilitado()) {
            return;
        }
        DadosResumo dados = carregarParaResumo(pacienteId);
        if (!dados.temHistorico()) {
            return;
        }
        ResumoHistoricoService.ResumoIa resumo = resumoHistoricoService.gerar(dados.historicoTexto());
        if (resumo == null) {
            return; // falha na IA: preserva o resumo anterior (não soma nada)
        }
        salvarResumo(pacienteId, resumo);
    }

    /** Contexto para gerar o resumo por IA (texto do histórico + flags), sem segurar transação na chamada lenta. */
    @Transactional(readOnly = true)
    public DadosResumo carregarParaResumo(Long pacienteId) {
        if (!pacienteRepository.existsById(pacienteId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado");
        }
        List<Prontuario> prontuarios = carregarProntuarios(pacienteId);
        return new DadosResumo(montarHistoricoTexto(prontuarios), iaHabilitada(), !prontuarios.isEmpty());
    }

    /** Grava o resumo gerado e ACUMULA o consumo de IA (tokens + custo + nº de gerações + modelo). */
    @Transactional
    public void salvarResumo(Long pacienteId, ResumoHistoricoService.ResumoIa resumo) {
        Paciente p = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado"));
        p.setResumoHistoricoIa(resumo.texto());
        p.setResumoHistoricoGeradoEm(LocalDateTime.now());
        p.setResumoHistoricoModeloIa(resumo.modelo());
        p.setResumoHistoricoTokensEntrada(zero(p.getResumoHistoricoTokensEntrada()) + resumo.tokensEntrada());
        p.setResumoHistoricoTokensSaida(zero(p.getResumoHistoricoTokensSaida()) + resumo.tokensSaida());
        BigDecimal custo = custoIaService.custoUsd(resumo.modelo(), resumo.tokensEntrada(), resumo.tokensSaida());
        p.setResumoHistoricoCustoUsd(zero(p.getResumoHistoricoCustoUsd()).add(custo == null ? BigDecimal.ZERO : custo));
        p.setResumoHistoricoGeracoes(zero(p.getResumoHistoricoGeracoes()) + 1);
        pacienteRepository.save(p);
        // Ledger de auditoria: uma linha por geração do resumo do histórico.
        usoIaService.registrar(UsoIaTipo.PRONTUARIO_RESUMO, "Resumo do histórico — " + p.getNome(),
                resumo.modelo(), resumo.tokensEntrada(), resumo.tokensSaida(), custo,
                "/prontuario-medico/" + p.getId());
    }

    private static long zero(Long v) {
        return v == null ? 0L : v;
    }

    private static int zero(Integer v) {
        return v == null ? 0 : v;
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public boolean iaHabilitada() {
        try {
            return configuracaoService.lerBooleano(ChaveConfiguracao.APP_PRONTUARIO_IA_HABILITADO);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Toggle específico do resumo do histórico (além do toggle geral da análise de documentos). */
    public boolean resumoIaHabilitado() {
        try {
            return configuracaoService.lerBooleano(ChaveConfiguracao.APP_PRONTUARIO_RESUMO_IA_HABILITADO);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Dados para a geração do resumo (fora da transação/chamada lenta). */
    public record DadosResumo(String historicoTexto, boolean iaHabilitada, boolean temHistorico) {
    }

    private List<Prontuario> carregarProntuarios(Long pacienteId) {
        return prontuarioRepository
                .search("", pacienteId, null, "", null,
                        Pageable.unpaged(Sort.by(Sort.Direction.DESC, "agendamento.dataHora")))
                .getContent();
    }

    /** Texto compacto da linha do tempo (com os resumos por documento) para a IA sintetizar. */
    private String montarHistoricoTexto(List<Prontuario> prontuarios) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Prontuario p : prontuarios) {
            if (i++ >= MAX_ATENDIMENTOS_RESUMO) {
                sb.append("\n(demais atendimentos mais antigos omitidos)\n");
                break;
            }
            Agendamento a = p.getAgendamento();
            sb.append("\n- Atendimento ")
                    .append(a.getDataHora() == null ? "(sem data)" : a.getDataHora().format(DATA))
                    .append(" · ").append(nome(a.getEspecialidade() == null ? null : a.getEspecialidade().getNome()))
                    .append(" · ").append(nome(a.getProcedimento() == null ? null : a.getProcedimento().getNome()))
                    .append(" · ").append(nome(a.getUnidadeSaude() == null ? null : a.getUnidadeSaude().getNome()));
            if (p.getStatusAlerta() != null) {
                sb.append(" [").append(p.getStatusAlerta().getDescricao()).append("]");
            }
            sb.append("\n");
            for (Documento d : p.getDocumentos()) {
                sb.append("    · ")
                        .append(d.getTipo() == null ? "Documento" : d.getTipo().getNome())
                        .append(" — ").append(nome(d.getNome()));
                if (d.getStatusAnalise() != null) {
                    sb.append(" [").append(d.getStatusAnalise().getDescricao()).append("]");
                }
                if (d.getResumoClinico() != null && !d.getResumoClinico().isBlank()) {
                    sb.append(": ").append(d.getResumoClinico().trim());
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String nome(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }
}
