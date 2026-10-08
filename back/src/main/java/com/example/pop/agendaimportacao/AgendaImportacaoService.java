package com.example.pop.agendaimportacao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.paciente.Documentos;
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.profissional.ProfissionalSaudeRepository;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Fase 1 da importação de agenda por Excel: dado o arquivo, PARSEIA (via {@link AgendaPlanilhaParser}) e
 * VALIDA cada campo, resolvendo profissional / especialidade / Configuração da Agenda / unidade contra os
 * cadastros existentes (por NOME ou CÓDIGO de integração) — apenas CONSULTA, nada é criado nem gravado.
 * Devolve o {@link AgendaImportPreviewResponse} com os campos resolvidos e os erros para o cliente corrigir a
 * planilha e reimportar. A gravação da agenda/horários fica para a Fase 2.
 *
 * <p>O rótulo visível de "Procedimento" nesta tela é <b>Configuração da Agenda</b> — a entidade/repositório
 * ainda se chamam {@code Procedimento} neste branch (renomeação acontece em paralelo); aqui usamos os nomes
 * existentes no código e o rótulo novo só nas mensagens.
 */
@Service
public class AgendaImportacaoService {

    private static final DateTimeFormatter DATA_SAIDA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<DateTimeFormatter> DATA_ACEITAS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    private static final List<DateTimeFormatter> HORA_ACEITAS = List.of(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"));

    /** Status assumido quando a coluna fica em branco (marcação nova aguarda o paciente confirmar). */
    private static final StatusAgendamento STATUS_PADRAO = StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE;

    private final AgendaPlanilhaParser parser;
    private final ProfissionalSaudeRepository profissionalRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ProcedimentoRepository procedimentoRepository;
    private final UnidadeRepository unidadeRepository;

    public AgendaImportacaoService(AgendaPlanilhaParser parser,
            ProfissionalSaudeRepository profissionalRepository,
            EspecialidadeRepository especialidadeRepository,
            ProcedimentoRepository procedimentoRepository,
            UnidadeRepository unidadeRepository) {
        this.parser = parser;
        this.profissionalRepository = profissionalRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.procedimentoRepository = procedimentoRepository;
        this.unidadeRepository = unidadeRepository;
    }

    /** Referência de cadastro para casar por nome/código (id só para a Fase 2). */
    private record RefCadastro(Long id, String nome, String codigo) {
    }

    @Transactional(readOnly = true)
    public AgendaImportPreviewResponse preview(byte[] bytes, String arquivo) {
        AgendaPlanilhaParser.Bruta bruta = parser.parse(bytes);
        Map<String, String> a = bruta.agenda();

        List<RefCadastro> profissionais = profissionalRepository.findAll().stream()
                .map(p -> new RefCadastro(p.getId(), p.getNome(), p.getCodigoIntegracao())).toList();
        List<RefCadastro> especialidades = especialidadeRepository.findAll().stream()
                .map(e -> new RefCadastro(e.getId(), e.getNome(), e.getCodigoIntegracao())).toList();
        List<RefCadastro> unidades = unidadeRepository.findAll().stream()
                .map(u -> new RefCadastro(u.getId(), u.getNome(), u.getCodigoIntegracao())).toList();
        // "Configuração da Agenda" (entidade Procedimento): não tem código de integração — casa só por nome.
        List<RefCadastro> configuracoes = procedimentoRepository.findAll().stream()
                .map(p -> new RefCadastro(p.getId(), p.getNome(), null)).toList();

        AgendaPreview agenda = new AgendaPreview(
                resolverData(a.get("data")),
                resolverRef(a.get("profissional"), "Profissional", profissionais),
                resolverRef(a.get("especialidade"), "Especialidade", especialidades),
                resolverRef(a.get("config"), "Configuração da Agenda", configuracoes),
                resolverRef(a.get("unidade"), "Unidade executante", unidades),
                CampoPreview.ok(a.getOrDefault("nome", ""))); // nome é opcional (rótulo livre)

        List<HorarioPreview> horarios = new ArrayList<>();
        for (AgendaPlanilhaParser.LinhaBruta l : bruta.horarios()) {
            horarios.add(resolverHorario(l));
        }

        int total = contarErros(agenda) + horarios.stream().mapToInt(AgendaImportacaoService::contarErros).sum();
        return new AgendaImportPreviewResponse(arquivo, agenda, horarios, horarios.size(), total);
    }

    // ---------------- Resolução de campos ----------------

    private CampoPreview resolverRef(String valor, String rotulo, List<RefCadastro> refs) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return CampoPreview.erro("", rotulo + " é obrigatório(a).");
        }
        // 1) casa por código de integração (quando o cadastro tem código).
        for (RefCadastro r : refs) {
            if (r.codigo() != null && !r.codigo().isBlank() && r.codigo().trim().equalsIgnoreCase(v)) {
                return CampoPreview.ok(valor, r.nome());
            }
        }
        // 2) casa por nome normalizado (sem acento/caixa).
        String alvo = AgendaPlanilhaParser.normalizar(v);
        List<RefCadastro> casam = refs.stream()
                .filter(r -> AgendaPlanilhaParser.normalizar(r.nome()).equals(alvo)).toList();
        if (casam.size() == 1) {
            return CampoPreview.ok(valor, casam.get(0).nome());
        }
        if (casam.isEmpty()) {
            return CampoPreview.erro(valor, rotulo + " não encontrado(a) pelo nome ou código.");
        }
        return CampoPreview.erro(valor, "Mais de um(a) " + rotulo.toLowerCase() + " com esse nome — use o código.");
    }

    private CampoPreview resolverData(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return CampoPreview.erro("", "Data é obrigatória.");
        }
        LocalDate data = parseData(v);
        return data == null
                ? CampoPreview.erro(valor, "Data inválida (use dd/mm/aaaa).")
                : CampoPreview.ok(valor, data.format(DATA_SAIDA));
    }

    private HorarioPreview resolverHorario(AgendaPlanilhaParser.LinhaBruta l) {
        CampoPreview paciente = l.paciente().isBlank()
                ? CampoPreview.erro("", "Nome do paciente é obrigatório.")
                : CampoPreview.ok(l.paciente());
        CampoPreview cpf = resolverCpf(l.cpf());
        CampoPreview inicio = resolverHora(l.inicio(), "Hora início", true);
        CampoPreview fim = resolverHora(l.fim(), "Hora fim", false);
        // Coerência: fim depois do início (só quando ambos são válidos).
        if (!inicio.erro() && !fim.erro() && !l.fim().isBlank()) {
            LocalTime i = parseHora(l.inicio());
            LocalTime f = parseHora(l.fim());
            if (i != null && f != null && !f.isAfter(i)) {
                fim = CampoPreview.erro(l.fim(), "Hora fim deve ser após a hora início.");
            }
        }
        CampoPreview status = resolverStatus(l.status());
        return new HorarioPreview(l.linhaExcel(), paciente, cpf, inicio, fim, status);
    }

    private CampoPreview resolverCpf(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return CampoPreview.erro("", "CPF é obrigatório.");
        }
        if (!Documentos.cpfValido(v)) {
            return CampoPreview.erro(valor, "CPF inválido.");
        }
        return CampoPreview.ok(valor, formatarCpf(Documentos.somenteDigitos(v)));
    }

    private CampoPreview resolverHora(String valor, String rotulo, boolean obrigatorio) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            return obrigatorio ? CampoPreview.erro("", rotulo + " é obrigatória.") : CampoPreview.ok("");
        }
        LocalTime hora = parseHora(v);
        return hora == null
                ? CampoPreview.erro(valor, rotulo + " inválida (use HH:mm).")
                : CampoPreview.ok(valor, hora.format(HORA_ACEITAS.get(0)));
    }

    private CampoPreview resolverStatus(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            // Em branco é aceito: assume o padrão (sem erro).
            return CampoPreview.ok("", STATUS_PADRAO.getDescricao() + " (padrão)");
        }
        String alvo = AgendaPlanilhaParser.normalizar(v);
        for (StatusAgendamento s : StatusAgendamento.values()) {
            if (AgendaPlanilhaParser.normalizar(s.name()).equals(alvo)
                    || AgendaPlanilhaParser.normalizar(s.getDescricao()).equals(alvo)) {
                return CampoPreview.ok(valor, s.getDescricao());
            }
        }
        return CampoPreview.erro(valor, "Status não reconhecido.");
    }

    // ---------------- Parse de data/hora ----------------

    private static LocalDate parseData(String v) {
        for (DateTimeFormatter f : DATA_ACEITAS) {
            try {
                return LocalDate.parse(v, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static LocalTime parseHora(String v) {
        for (DateTimeFormatter f : HORA_ACEITAS) {
            try {
                return LocalTime.parse(v, f);
            } catch (RuntimeException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    // ---------------- Helpers ----------------

    private static int contarErros(AgendaPreview a) {
        return conta(a.data(), a.profissional(), a.especialidade(), a.configuracaoAgenda(), a.unidade(), a.nome());
    }

    private static int contarErros(HorarioPreview h) {
        return conta(h.paciente(), h.cpf(), h.horaInicio(), h.horaFim(), h.status());
    }

    private static int conta(CampoPreview... campos) {
        int n = 0;
        for (CampoPreview c : campos) {
            if (c != null && c.erro()) {
                n++;
            }
        }
        return n;
    }

    private static String formatarCpf(String digitos) {
        if (digitos == null || digitos.length() != 11) {
            return digitos;
        }
        return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "."
                + digitos.substring(6, 9) + "-" + digitos.substring(9);
    }
}
