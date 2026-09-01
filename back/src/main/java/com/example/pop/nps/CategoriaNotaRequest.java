package com.example.pop.nps;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Nota (em estrelas, 1 a 5) de uma categoria enviada pelo paciente ao responder o NPS. */
public record CategoriaNotaRequest(
        @NotNull Long categoriaId,
        @NotNull @Min(1) @Max(5) Integer nota) {
}
