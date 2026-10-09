package com.example.pop.lembrete;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.pop.inquilino.ExecucaoPorInquilino;

/** Dispara os lembretes na hora certa (a cada 5 minutos), uma vez por inquilino. */
@Component
public class LembreteScheduler {

    private static final Logger log = LoggerFactory.getLogger(LembreteScheduler.class);

    private final LembreteService lembreteService;
    private final ExecucaoPorInquilino execucaoPorInquilino;

    public LembreteScheduler(LembreteService lembreteService, ExecucaoPorInquilino execucaoPorInquilino) {
        this.lembreteService = lembreteService;
        this.execucaoPorInquilino = execucaoPorInquilino;
    }

    // A cada 5 min; começa 1 min após subir (dá tempo do contexto inicializar).
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void disparar() {
        try {
            // O scheduler roda sem request → sem inquilino: itera cada inquilino com o tenant fixado.
            execucaoPorInquilino.paraCadaInquilinoAtivo(schema -> {
                int enviados = lembreteService.dispararPendentes();
                if (enviados > 0) {
                    log.info("Lembretes disparados ({}): {}", schema, enviados);
                }
            });
        } catch (RuntimeException e) {
            // Nunca deixa o job morrer por um erro pontual; tenta de novo no próximo ciclo.
            log.warn("Falha ao disparar lembretes: {}", e.getMessage());
        }
    }
}
