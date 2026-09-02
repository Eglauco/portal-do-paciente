package com.example.pop.dashboard;

import java.util.List;

/** Métricas de agendamentos no período (janela de N dias sobre data_hora). */
public record AgendamentoDashboard(
        int dias,
        long total, long anterior,
        long aguardando, long confirmados, long presencas, long faltas,
        long canceladosUnidade, long canceladosPaciente,
        double taxaComparecimento, double taxaConfirmacao, double taxaCancelamento,
        long proximos7Dias,
        List<SerieDiaria> porDia,
        List<Fatia> porStatus,
        List<ItemContagem> topProcedimentos,
        List<ItemContagem> topProfissionais,
        List<ItemContagem> porEspecialidade,
        List<ItemContagem> motivosFalta) {
}
