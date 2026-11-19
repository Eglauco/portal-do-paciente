package com.example.pop.agendamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.pop.inquilino.ExecucaoPorInquilino;

/**
 * Roda a regra da confirmação vencida a cada 1 minuto, uma vez por inquilino ativo. Unidades com o prazo
 * em 0 (padrão) não são afetadas. Um erro num inquilino não impede os demais (isolado no
 * {@link ExecucaoPorInquilino}); um erro geral é logado e o job tenta de novo no minuto seguinte.
 */
@Component
public class ConfirmacaoVencidaScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoVencidaScheduler.class);

    private final ConfirmacaoVencidaService service;
    private final ExecucaoPorInquilino execucaoPorInquilino;

    public ConfirmacaoVencidaScheduler(ConfirmacaoVencidaService service, ExecucaoPorInquilino execucaoPorInquilino) {
        this.service = service;
        this.execucaoPorInquilino = execucaoPorInquilino;
    }

    // A cada 1 min; começa 1 min após subir (dá tempo do contexto e das migrations).
    @Scheduled(fixedRate = 60 * 1000, initialDelay = 60 * 1000)
    public void executar() {
        try {
            execucaoPorInquilino.paraCadaInquilinoAtivo(schema -> {
                int vencidos = service.vencerConfirmacoes();
                if (vencidos > 0) {
                    log.info("Confirmações vencidas ({}): {}", schema, vencidos);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Falha ao aplicar confirmações vencidas: {}", e.getMessage());
        }
    }
}
