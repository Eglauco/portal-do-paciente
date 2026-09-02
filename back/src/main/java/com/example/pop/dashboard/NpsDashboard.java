package com.example.pop.dashboard;

import java.util.List;

/**
 * Métricas de NPS (modelo de 1–5 estrelas por categoria; média = média das notas).
 * Satisfação deriva da média por avaliação: >=4 satisfeito, >=3 neutro, <3 insatisfeito.
 */
public record NpsDashboard(
        int dias,
        long gerados, long anterior,
        long respondidos, long pendentes, long expirados,
        double taxaResposta, Double mediaGeral,
        long satisfeitos, long neutros, long insatisfeitos,
        List<SerieDiaria> avaliacoesPorDia,
        List<Fatia> porStatus,
        List<MediaCategoria> mediaPorCategoria,
        List<ItemContagem> distribuicaoNotas) {
}
