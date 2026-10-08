package com.example.pop.configuracaoagenda;

import java.time.LocalDateTime;

/** Item do cadastro de documentos TCLE de um configuracaoAgenda (admin). */
public record TermoConfiguracaoAgendaResponse(Long id, String nome, OrigemModeloTermo origemModelo, String url,
        String contentType, String providerTemplateToken, String modeloProviderNome, boolean profissionalAssina,
        boolean profissionalCertificado, LocalDateTime criadoEm) {

    public static TermoConfiguracaoAgendaResponse from(TermoConfiguracaoAgenda t) {
        return new TermoConfiguracaoAgendaResponse(t.getId(), t.getNome(), t.getOrigemModelo(), t.getUrl(),
                t.getContentType(), t.getProviderTemplateToken(), t.getModeloProviderNome(),
                t.isProfissionalAssina(), t.isProfissionalCertificado(), t.getCriadoEm());
    }
}
