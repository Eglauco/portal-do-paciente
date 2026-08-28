package com.example.pop.postagem;

import java.time.LocalDateTime;

import com.example.pop.common.Ref;

/** Detalhe da postagem (admin — edição). */
public record PostagemDetalheResponse(
        Long id,
        String titulo,
        String descricao,
        boolean mostrarTotalCurtidas,
        boolean habilitarComentarios,
        Ref unidadeSaude,
        String url,
        LocalDateTime criadoEm,
        long totalCurtidas,
        long totalComentarios) {
}
