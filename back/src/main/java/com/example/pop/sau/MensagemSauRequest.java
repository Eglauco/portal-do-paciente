package com.example.pop.sau;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Uma resposta na thread de uma manifestação (paciente ou SAU). */
public record MensagemSauRequest(@NotBlank @Size(max = 4000) String texto) {
}
