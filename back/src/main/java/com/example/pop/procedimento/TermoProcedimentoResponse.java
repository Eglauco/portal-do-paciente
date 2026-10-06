package com.example.pop.procedimento;

import java.time.LocalDateTime;

/** Item do cadastro de documentos TCLE de um procedimento (admin). */
public record TermoProcedimentoResponse(Long id, String nome, OrigemModeloTermo origemModelo, String url,
        String contentType, String providerTemplateToken, String modeloProviderNome, boolean profissionalAssina,
        boolean profissionalCertificado, LocalDateTime criadoEm) {

    public static TermoProcedimentoResponse from(TermoProcedimento t) {
        return new TermoProcedimentoResponse(t.getId(), t.getNome(), t.getOrigemModelo(), t.getUrl(),
                t.getContentType(), t.getProviderTemplateToken(), t.getModeloProviderNome(),
                t.isProfissionalAssina(), t.isProfissionalCertificado(), t.getCriadoEm());
    }
}
