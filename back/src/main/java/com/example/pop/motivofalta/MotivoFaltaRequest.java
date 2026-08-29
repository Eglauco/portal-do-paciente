package com.example.pop.motivofalta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação/edição de um motivo de falta. {@code ativo} usa wrapper
 * ({@link Boolean}) para tolerar corpo sem o campo (ou nulo) sem quebrar a
 * desserialização; quando ausente, mantém o valor atual (edição) ou o padrão.
 */
public record MotivoFaltaRequest(
        @NotBlank @Size(max = 120) String motivo,
        Boolean ativo) {
}
