package com.example.pop.pacienteauth;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Início do login: identidade (telefone + CPF + data) para saber se a conta tem senha. Não envia SMS. */
public record IniciarLoginRequest(
        @NotBlank String cpf,
        @NotNull LocalDate dataNascimento,
        @NotBlank String telefone) {
}
