package com.example.pop.sau;

/** Tipo de manifestação (CRUD do admin e seleção do app). */
public record TipoManifestacaoResponse(Long id, String nome, String descricao, boolean ativo) {

    public static TipoManifestacaoResponse from(TipoManifestacao t) {
        return new TipoManifestacaoResponse(t.getId(), t.getNome(), t.getDescricao(), t.isAtivo());
    }
}
