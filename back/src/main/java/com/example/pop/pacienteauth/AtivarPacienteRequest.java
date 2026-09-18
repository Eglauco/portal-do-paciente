package com.example.pop.pacienteauth;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Ativação do app: telefone + CPF + data de nascimento (identidade) + código do OTP + id do aparelho. */
public record AtivarPacienteRequest(
        @NotBlank String cpf,
        @NotNull LocalDate dataNascimento,
        @NotBlank String codigo,
        @NotBlank String dispositivoId,
        @NotBlank String telefone) {
}
