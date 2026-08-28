package com.example.pop.nps;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Nota de uma categoria enviada pelo paciente ao responder o NPS. */
public record CategoriaNotaRequest(
        @NotNull Long categoriaId,
        @NotNull @Min(0) @Max(10) Integer nota) {
}
