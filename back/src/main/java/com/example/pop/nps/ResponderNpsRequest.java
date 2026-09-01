package com.example.pop.nps;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** Resposta do NPS: uma nota em estrelas (1 a 5) por categoria + observação opcional. */
public record ResponderNpsRequest(
        @NotEmpty @Valid List<CategoriaNotaRequest> notas,
        String observacao) {
}
