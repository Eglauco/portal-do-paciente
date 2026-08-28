package com.example.pop.postagem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostagemRequest(
        @NotBlank String titulo,
        String descricao,
        boolean mostrarTotalCurtidas,
        boolean habilitarComentarios,
        @NotNull Long unidadeSaudeId,
        @NotBlank String url) {
}
