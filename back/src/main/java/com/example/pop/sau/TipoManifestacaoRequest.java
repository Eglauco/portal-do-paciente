package com.example.pop.sau;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Criação/edição de um tipo de manifestação. {@code ativo} é wrapper para tolerar
 * corpo sem o campo (mantém o valor atual na edição, ou o padrão true na criação).
 */
public record TipoManifestacaoRequest(
        @NotBlank @Size(max = 80) String nome,
        @Size(max = 200) String descricao,
        Boolean ativo) {
}
