package com.example.pop.auth;

import jakarta.validation.constraints.NotBlank;

/** Credenciais enviadas pelo admin no login. */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String senha) {
}
