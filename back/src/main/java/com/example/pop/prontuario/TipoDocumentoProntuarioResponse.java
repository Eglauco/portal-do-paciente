package com.example.pop.prontuario;

/** Retorno do Tipo de Documento do Prontuário (cadastro). */
public record TipoDocumentoProntuarioResponse(
        Long id,
        String nome,
        String promptResumo,
        String promptValidacao,
        boolean ativo) {

    public static TipoDocumentoProntuarioResponse from(TipoDocumentoProntuario t) {
        return new TipoDocumentoProntuarioResponse(
                t.getId(), t.getNome(), t.getPromptResumo(), t.getPromptValidacao(), t.isAtivo());
    }
}
