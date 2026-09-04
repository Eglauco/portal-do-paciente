package com.example.pop.pacienteauth;

import jakarta.validation.constraints.NotBlank;

/** Pedido do paciente para receber o código de ativação por SMS. */
public record SolicitarCodigoRequest(@NotBlank String telefone) {
}
