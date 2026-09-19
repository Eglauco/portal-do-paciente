package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.notificacaoadmin.NotificacaoAdminService;
import com.example.pop.notificacaoadmin.TipoNotificacaoAdmin;
import com.example.pop.prontuario.AnaliseDocumentoService.AnaliseResultado;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Camada TRANSACIONAL da análise de documentos por IA: carrega o contexto (com o gate), aplica o
 * resultado (status + resumo + rollup do prontuário + notificação da unidade) e faz a validação
 * humana. A chamada lenta ao Claude fica no {@link ProntuarioIaOrchestrator} (assíncrono).
 */
@Service
public class ProntuarioIaService {

    private final DocumentoRepository documentoRepository;
    private final ProntuarioRepository prontuarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracaoService configuracaoService;
    private final NotificacaoAdminService notificacaoAdminService;

    public ProntuarioIaService(DocumentoRepository documentoRepository, ProntuarioRepository prontuarioRepository,
            UsuarioRepository usuarioRepository, ConfiguracaoService configuracaoService,
            NotificacaoAdminService notificacaoAdminService) {
        this.documentoRepository = documentoRepository;
        this.prontuarioRepository = prontuarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracaoService = configuracaoService;
        this.notificacaoAdminService = notificacaoAdminService;
    }

    /** Contexto para a IA analisar um documento (dados desanexados). */
    public record ContextoAnalise(String url, String nome, String promptResumo, String promptValidacao) {
    }

    /**
     * Carrega o contexto se — e só se — a IA deve analisar: recurso ligado globalmente, documento
     * existe com URL, tipo definido com promptResumo, e (salvo {@code forcar}=reanalisar) ainda não
     * analisado. Caso contrário, vazio.
     */
    @Transactional(readOnly = true)
    public Optional<ContextoAnalise> carregarContexto(Long documentoId, boolean forcar) {
        if (!iaHabilitada()) {
            return Optional.empty();
        }
        Documento d = documentoRepository.findById(documentoId).orElse(null);
        if (d == null || d.getUrl() == null || d.getUrl().isBlank()) {
            return Optional.empty();
        }
        if (!forcar && d.getStatusAnalise() != StatusAnaliseDocumento.NAO_ANALISADO) {
            return Optional.empty(); // já analisado (não reanalisa em toda edição do prontuário)
        }
        TipoDocumentoProntuario tipo = d.getTipo();
        if (tipo == null || tipo.getPromptResumo() == null || tipo.getPromptResumo().isBlank()) {
            return Optional.empty(); // sem direção → IA não roda
        }
        return Optional.of(new ContextoAnalise(d.getUrl(), d.getNome(), tipo.getPromptResumo(), tipo.getPromptValidacao()));
    }

    /**
     * Grava o resultado da IA no documento, recalcula o status do prontuário e notifica se for alerta.
     * Devolve o id do PACIENTE (para regenerar o resumo do histórico depois), ou null se não aplicou.
     */
    @Transactional
    public Long aplicarResultado(Long documentoId, AnaliseResultado resultado) {
        Documento d = documentoRepository.findById(documentoId).orElse(null);
        if (d == null) {
            return null;
        }
        d.setStatusAnalise(resultado.status());
        if (resultado.resumo() != null) {
            d.setResumoClinico(resultado.resumo());
        }
        d.setAnalisadoEm(LocalDateTime.now());
        // Uma (re)análise invalida qualquer validação humana anterior.
        d.setValidadoPor(null);
        d.setValidadoEm(null);
        documentoRepository.save(d);

        Prontuario p = d.getProntuario();
        recomputarStatus(p);
        prontuarioRepository.save(p);

        if (resultado.status() == StatusAnaliseDocumento.AGUARDANDO_VALIDACAO) {
            notificarUnidade(p, d);
        }
        return p.getAgendamento().getPaciente().getId();
    }

    /** Validação humana do alerta: registra quem validou e quando; recalcula o prontuário. */
    @Transactional
    public Prontuario validar(Long documentoId, Long usuarioId) {
        Documento d = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado"));
        if (d.getStatusAnalise() != StatusAnaliseDocumento.AGUARDANDO_VALIDACAO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este documento não está aguardando validação.");
        }
        Usuario u = usuarioId == null ? null : usuarioRepository.findById(usuarioId).orElse(null);
        d.setStatusAnalise(StatusAnaliseDocumento.VALIDADO);
        d.setValidadoPor(u);
        d.setValidadoEm(LocalDateTime.now());
        documentoRepository.save(d);

        Prontuario p = d.getProntuario();
        recomputarStatus(p);
        return prontuarioRepository.save(p);
    }

    /** Rollup do status de alerta do prontuário a partir dos status dos documentos. */
    private void recomputarStatus(Prontuario p) {
        boolean aguardando = false;
        boolean validado = false;
        for (Documento d : p.getDocumentos()) {
            if (d.getStatusAnalise() == StatusAnaliseDocumento.AGUARDANDO_VALIDACAO) {
                aguardando = true;
            } else if (d.getStatusAnalise() == StatusAnaliseDocumento.VALIDADO) {
                validado = true;
            }
        }
        p.setStatusAlerta(aguardando ? StatusAlertaProntuario.AGUARDANDO_VALIDACAO
                : validado ? StatusAlertaProntuario.VALIDADO : StatusAlertaProntuario.SEM_ALTERACOES);
    }

    /** Notifica os admins da unidade (sino) sobre o documento aguardando validação — após o commit. */
    private void notificarUnidade(Prontuario p, Documento d) {
        Agendamento a = p.getAgendamento();
        Long unidadeId = a.getUnidadeSaude() == null ? null : a.getUnidadeSaude().getId();
        String paciente = a.getPaciente() == null ? "" : a.getPaciente().getNome();
        Long documentoId = d.getId();
        Long prontuarioId = p.getId();
        String nomeDoc = d.getNome();
        aposCommit(() -> notificacaoAdminService.registrar(
                TipoNotificacaoAdmin.PRONTUARIO_VALIDACAO, unidadeId,
                "Documento aguardando validação",
                "O documento \"" + nomeDoc + "\" de " + paciente + " precisa de validação (alerta da IA).",
                documentoId, "/prontuarios/" + prontuarioId));
    }

    private boolean iaHabilitada() {
        try {
            return configuracaoService.lerBooleano(ChaveConfiguracao.APP_PRONTUARIO_IA_HABILITADO);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }
}
