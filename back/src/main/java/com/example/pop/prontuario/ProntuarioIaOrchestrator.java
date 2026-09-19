package com.example.pop.prontuario;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orquestra a análise de documentos por IA em BACKGROUND (não trava o salvar do prontuário): carrega
 * o contexto (curto, transacional) → chama o Claude (lento) → aplica o resultado. Disparado após
 * salvar um documento novo, e pelo botão "Reanalisar" (forcar=true).
 */
@Service
public class ProntuarioIaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProntuarioIaOrchestrator.class);

    private final ProntuarioIaService prontuarioIaService;
    private final AnaliseDocumentoService analiseDocumentoService;
    private final ProntuarioMedicoService prontuarioMedicoService;

    public ProntuarioIaOrchestrator(ProntuarioIaService prontuarioIaService,
            AnaliseDocumentoService analiseDocumentoService, ProntuarioMedicoService prontuarioMedicoService) {
        this.prontuarioIaService = prontuarioIaService;
        this.analiseDocumentoService = analiseDocumentoService;
        this.prontuarioMedicoService = prontuarioMedicoService;
    }

    @Async("prontuarioIaExecutor")
    public void analisar(Long documentoId, boolean forcar) {
        try {
            Optional<ProntuarioIaService.ContextoAnalise> ctxOpt =
                    prontuarioIaService.carregarContexto(documentoId, forcar);
            if (ctxOpt.isEmpty()) {
                return;
            }
            ProntuarioIaService.ContextoAnalise c = ctxOpt.get();
            AnaliseDocumentoService.AnaliseResultado r =
                    analiseDocumentoService.analisar(c.url(), c.nome(), c.promptResumo(), c.promptValidacao());
            Long pacienteId = prontuarioIaService.aplicarResultado(documentoId, r);
            // Regenera automaticamente o resumo do histórico do paciente (gated + fail-safe internamente).
            if (pacienteId != null) {
                prontuarioMedicoService.regenerarResumo(pacienteId);
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao analisar o documento {} por IA: {}", documentoId, e.toString());
        }
    }
}
