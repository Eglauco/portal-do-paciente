package com.example.pop.postagem;

import java.time.LocalDateTime;

import com.example.pop.common.Ref;

/** Item do feed (app). */
public record FeedResponse(
        Long id,
        String titulo,
        String descricao,
        Ref unidadeSaude,
        String url,
        boolean mostrarTotalCurtidas,
        long totalCurtidas,
        boolean habilitarComentarios,
        long totalComentarios,
        boolean curtidoPorMim,
        LocalDateTime criadoEm) {
}
