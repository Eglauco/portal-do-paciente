package com.example.pop.credencialia;

/**
 * Um turno da conversa enviada à IA (provider-agnostic). {@code anexo} só no turno do usuário que carrega
 * um documento/imagem (ex.: a análise de prontuário); nos demais é null.
 */
public record Turno(Papel papel, String texto, Anexo anexo) {

    public enum Papel { USUARIO, ASSISTENTE }

    public static Turno usuario(String texto) {
        return new Turno(Papel.USUARIO, texto, null);
    }

    public static Turno usuario(String texto, Anexo anexo) {
        return new Turno(Papel.USUARIO, texto, anexo);
    }

    public static Turno assistente(String texto) {
        return new Turno(Papel.ASSISTENTE, texto, null);
    }
}
