package com.example.pop.postagem;

import java.time.LocalDateTime;

import com.example.pop.common.Ref;

/** Item da listagem de postagens (admin). */
public record PostagemResponse(
        Long id,
        String titulo,
        Ref unidadeSaude,
        boolean mostrarTotalCurtidas,
        boolean habilitarComentarios,
        String url,
        LocalDateTime criadoEm,
        long totalCurtidas,
        long totalComentarios) {
}
