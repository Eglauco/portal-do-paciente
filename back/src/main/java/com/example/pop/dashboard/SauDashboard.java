package com.example.pop.dashboard;

import java.util.List;

/** Métricas do SAU. Estados são retrato atual; volume é medido na janela de N dias. */
public record SauDashboard(
        int dias,
        long total, long anterior,
        long aguardandoSau, long aguardandoPaciente, long fechadas, long abertas,
        double taxaResolucao,
        long mensagensPaciente, long mensagensSau,
        List<SerieDiaria> porDia,
        List<Fatia> porStatus,
        List<ItemContagem> porTipo,
        List<ItemContagem> cargaPorAtendente) {
}
