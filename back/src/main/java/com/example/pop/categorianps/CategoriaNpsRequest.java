package com.example.pop.categorianps;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação/edição de uma categoria de NPS. {@code ativo} usa wrapper
 * ({@link Boolean}) para tolerar corpo sem o campo (ou nulo) sem quebrar a
 * desserialização; quando ausente, mantém o valor atual (edição) ou o padrão.
 */
public record CategoriaNpsRequest(
        @NotBlank @Size(max = 120) String nome,
        Boolean ativo) {
}
