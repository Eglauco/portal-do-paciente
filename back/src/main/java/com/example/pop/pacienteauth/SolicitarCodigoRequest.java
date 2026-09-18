package com.example.pop.pacienteauth;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Pedido do código por SMS: telefone + CPF + data de nascimento (travas de identidade). */
public record SolicitarCodigoRequest(
        @NotBlank String cpf,
        @NotNull LocalDate dataNascimento,
        @NotBlank String telefone) {
}
