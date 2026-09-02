package com.example.pop.dashboard;

import java.util.List;

/**
 * Métricas de Chats ao vivo. Contagens de estado ("abertas agora", por status,
 * sem responsável) são um retrato ATUAL da unidade; volumes (conversas/mensagens)
 * são medidos na janela de N dias.
 */
public record ChatDashboard(
        int dias,
        long conversasPeriodo, long anterior,
        long naoLidas, long aguardando, long emAtendimento, long resolvidas,
        long abertas, long semResponsavel,
        long mensagensPeriodo, long mensagensPaciente, long mensagensUnidade,
        double taxaResolucao,
        List<SerieDiaria> conversasPorDia,
        List<Fatia> porStatus,
        List<ItemContagem> cargaPorAtendente) {
}
