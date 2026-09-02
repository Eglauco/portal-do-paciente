package com.example.pop.dashboard;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboards do back-office (somente leitura). Todos aceitam {@code unidadeId}
 * (a unidade ativa do gestor; ausente = todas) e {@code dias} (janela; 1–365).
 */
@RestController
@RequestMapping("/dashboard")
@Transactional(readOnly = true)
public class DashboardController {

    private static final int DIAS_PADRAO = 30;
    private static final int DIAS_MAX = 365;

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    private int normalizarDias(int dias) {
        return Math.min(Math.max(dias, 1), DIAS_MAX);
    }

    @GetMapping("/geral")
    public GeralDashboard geral(@RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "" + DIAS_PADRAO) int dias) {
        return service.geral(unidadeId, normalizarDias(dias));
    }

    @GetMapping("/agendamentos")
    public AgendamentoDashboard agendamentos(@RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "" + DIAS_PADRAO) int dias) {
        return service.agendamentos(unidadeId, normalizarDias(dias));
    }

    @GetMapping("/chats")
    public ChatDashboard chats(@RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "" + DIAS_PADRAO) int dias) {
        return service.chats(unidadeId, normalizarDias(dias));
    }

    @GetMapping("/sau")
    public SauDashboard sau(@RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "" + DIAS_PADRAO) int dias) {
        return service.sau(unidadeId, normalizarDias(dias));
    }

    @GetMapping("/nps")
    public NpsDashboard nps(@RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "" + DIAS_PADRAO) int dias) {
        return service.nps(unidadeId, normalizarDias(dias));
    }
}
