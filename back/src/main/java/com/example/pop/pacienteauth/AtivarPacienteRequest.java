package com.example.pop.pacienteauth;

import jakarta.validation.constraints.NotBlank;

/** Ativação do app: telefone + código (emitido pela unidade) + id do aparelho. */
public record AtivarPacienteRequest(
        @NotBlank String telefone,
        @NotBlank String codigo,
        @NotBlank String dispositivoId) {
}
