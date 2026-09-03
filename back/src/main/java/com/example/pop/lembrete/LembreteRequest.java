package com.example.pop.lembrete;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro/edição de um lembrete do procedimento (admin). */
public record LembreteRequest(
        @NotBlank @Size(max = 300) String texto,
        // Antecedência em horas: de 1h até 8760h (~1 ano).
        @NotNull @Min(1) @Max(8760) Integer horasAntecedencia) {
}
