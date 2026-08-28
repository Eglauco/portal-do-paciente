package com.example.pop.auth;

import jakarta.validation.constraints.NotNull;

/** Troca a unidade de saúde ativa do usuário logado. */
public record TrocarUnidadeRequest(@NotNull Long unidadeSaudeId) {
}
