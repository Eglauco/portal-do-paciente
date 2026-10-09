package com.example.pop.inquilino;

/** Dados de um inquilino para a tela de super-admin. */
public record InquilinoResponse(Long id, String nome, String schemaName, String situacao) {

    public static InquilinoResponse from(Inquilino inquilino) {
        return new InquilinoResponse(inquilino.getId(), inquilino.getNome(), inquilino.getSchemaName(),
                inquilino.getSituacao());
    }
}
