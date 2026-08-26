package com.example.pop.prontuario;

public record DocumentoResponse(Long id, String nome, String url) {

    public static DocumentoResponse from(Documento d) {
        return new DocumentoResponse(d.getId(), d.getNome(), d.getUrl());
    }
}
