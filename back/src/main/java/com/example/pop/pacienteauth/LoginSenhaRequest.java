package com.example.pop.pacienteauth;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Login por senha (sem SMS): identidade (telefone + CPF + data) + PIN + id do aparelho. */
public record LoginSenhaRequest(
        @NotBlank String cpf,
        @NotNull LocalDate dataNascimento,
        @NotBlank String telefone,
        @NotBlank String senha,
        @NotBlank String dispositivoId) {
}
