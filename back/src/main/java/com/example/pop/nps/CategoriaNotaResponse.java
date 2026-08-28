package com.example.pop.nps;

/** Nota de uma categoria dentro do detalhe do NPS. */
public record CategoriaNotaResponse(Long categoriaId, String categoria, int nota) {

    public static CategoriaNotaResponse from(NpsCategoriaNota n) {
        return new CategoriaNotaResponse(n.getCategoria().getId(), n.getCategoria().getNome(), n.getNota());
    }
}
