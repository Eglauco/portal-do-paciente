package com.example.pop.nps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara os NPS agendados na hora certa (a cada 5 minutos). */
@Component
public class NpsScheduler {

    private static final Logger log = LoggerFactory.getLogger(NpsScheduler.class);

    private final NpsService npsService;

    public NpsScheduler(NpsService npsService) {
        this.npsService = npsService;
    }

    // A cada 5 min; começa 1 min após subir (dá tempo do contexto inicializar).
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void disparar() {
        try {
            int enviados = npsService.dispararAgendados();
            if (enviados > 0) {
                log.info("NPS agendados disparados: {}", enviados);
            }
        } catch (RuntimeException e) {
            // Nunca deixa o job morrer por um erro pontual; tenta de novo no próximo ciclo.
            log.warn("Falha ao disparar NPS agendados: {}", e.getMessage());
        }
    }
}
