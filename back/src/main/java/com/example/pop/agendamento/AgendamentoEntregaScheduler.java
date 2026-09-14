package com.example.pop.agendamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Confere periodicamente os receipts da Expo para confirmar a ENTREGA ao aparelho das
 * notificações de agendamento (promove NOTIFICACAO_ENVIADA → NOTIFICACAO_ENTREGUE).
 */
@Component
public class AgendamentoEntregaScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoEntregaScheduler.class);

    private final AgendamentoEntregaService entregaService;

    public AgendamentoEntregaScheduler(AgendamentoEntregaService entregaService) {
        this.entregaService = entregaService;
    }

    // A cada 5 min; começa 1 min após subir (dá tempo do contexto inicializar).
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void conferirReceipts() {
        try {
            entregaService.resolverReceiptsPendentes();
        } catch (RuntimeException e) {
            // Nunca deixa o job morrer por um erro pontual; tenta de novo no próximo ciclo.
            log.warn("Falha ao conferir receipts de entrega: {}", e.getMessage());
        }
    }
}
