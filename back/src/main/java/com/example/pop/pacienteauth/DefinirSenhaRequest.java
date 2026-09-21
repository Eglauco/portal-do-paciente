package com.example.pop.pacienteauth;

import jakarta.validation.constraints.NotBlank;

/** Definição da senha (PIN) inicial, após o OTP. */
public record DefinirSenhaRequest(@NotBlank String senha) {
}
