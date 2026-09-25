package com.example.pop.procedimento;

import java.time.LocalDateTime;

/** Item do cadastro de documentos TCLE de um procedimento (admin). */
public record TermoProcedimentoResponse(Long id, String nome, String url, String contentType,
        LocalDateTime criadoEm) {

    public static TermoProcedimentoResponse from(TermoProcedimento t) {
        return new TermoProcedimentoResponse(t.getId(), t.getNome(), t.getUrl(), t.getContentType(), t.getCriadoEm());
    }
}
