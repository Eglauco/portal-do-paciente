package com.example.pop.postagem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostagemRequest(
        @NotBlank String titulo,
        String descricao,
        boolean mostrarTotalCurtidas,
        boolean habilitarComentarios,
        /** Liga a validação de comentários novos por IA (Claude) antes de publicar. */
        boolean validarComentariosIa,
        @NotNull Long unidadeSaudeId,
        @NotBlank String url) {

    /** Conveniência (sem validação por IA) para chamadas/testes anteriores a essa opção. */
    public PostagemRequest(String titulo, String descricao, boolean mostrarTotalCurtidas,
            boolean habilitarComentarios, Long unidadeSaudeId, String url) {
        this(titulo, descricao, mostrarTotalCurtidas, habilitarComentarios, false, unidadeSaudeId, url);
    }
}
