package com.example.pop.dashboard;

import java.util.List;

/** Visão consolidada (tela inicial): um KPI-chave por domínio + poucos gráficos. */
public record GeralDashboard(
        int dias,
        long agendamentosPeriodo, long agendamentosAnterior,
        long presencas, long faltas, double taxaComparecimento,
        long chatsAbertos, long chatsPeriodo,
        long sauAbertas, long sauPeriodo,
        long npsRespondidos, Double npsMedia,
        long pacientesUsandoApp, long pacientesAtivos, long pacientesTotal,
        List<SerieDiaria> agendamentosPorDia,
        List<Fatia> agendamentosPorStatus) {
}
