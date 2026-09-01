package com.example.pop.sau;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Abertura de uma manifestação pelo paciente (tipo + unidade + primeira mensagem). */
public record AbrirManifestacaoRequest(
        @NotNull Long tipoId,
        @NotNull Long unidadeId,
        @NotBlank @Size(max = 4000) String texto) {
}
