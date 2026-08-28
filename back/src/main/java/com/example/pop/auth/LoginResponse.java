package com.example.pop.auth;

import java.time.Instant;

/** Resposta do login: token JWT + dados básicos do usuário. */
public record LoginResponse(
        String token,
        String nome,
        String email,
        Instant expiraEm) {
}
