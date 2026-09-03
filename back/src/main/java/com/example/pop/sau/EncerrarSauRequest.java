package com.example.pop.sau;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Paciente encerra a manifestação avaliando o atendimento (nota obrigatória 1-5). */
public record EncerrarSauRequest(
        @NotNull @Min(1) @Max(5) Integer nota,
        @Size(max = 500) String comentario) {
}
