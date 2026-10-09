package com.example.pop.agendamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.pop.inquilino.ExecucaoPorInquilino;

/**
 * Confere periodicamente os receipts da Expo para confirmar a ENTREGA ao aparelho das
 * notificações de agendamento (promove NOTIFICACAO_ENVIADA → NOTIFICACAO_ENTREGUE), por inquilino.
 */
@Component
public class HorarioEntregaScheduler {

    private static final Logger log = LoggerFactory.getLogger(HorarioEntregaScheduler.class);

    private final HorarioEntregaService entregaService;
    private final ExecucaoPorInquilino execucaoPorInquilino;

    public HorarioEntregaScheduler(HorarioEntregaService entregaService, ExecucaoPorInquilino execucaoPorInquilino) {
        this.entregaService = entregaService;
        this.execucaoPorInquilino = execucaoPorInquilino;
    }

    // A cada 5 min; começa 1 min após subir (dá tempo do contexto inicializar).
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void conferirReceipts() {
        try {
            // O scheduler roda sem request → sem inquilino: itera cada inquilino com o tenant fixado.
            execucaoPorInquilino.paraCadaInquilinoAtivo(schema -> entregaService.resolverReceiptsPendentes());
        } catch (RuntimeException e) {
            // Nunca deixa o job morrer por um erro pontual; tenta de novo no próximo ciclo.
            log.warn("Falha ao conferir receipts de entrega: {}", e.getMessage());
        }
    }
}
