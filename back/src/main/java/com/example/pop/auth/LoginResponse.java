package com.example.pop.auth;

import java.time.Instant;

/** Resposta do login: token JWT + dados básicos do usuário (incluindo a unidade ativa). */
public record LoginResponse(
        String token,
        String nome,
        String email,
        Long unidadeSaudeId,
        String unidadeSaudeNome,
        Instant expiraEm) {
}
