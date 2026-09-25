package com.example.pop.procedimento;

/** Uma variável dinâmica do TCLE para a tela (token a copiar + descrição + exemplo + grupo). */
public record VariavelTermoResponse(String token, String descricao, String exemplo, String grupo) {

    public static VariavelTermoResponse from(VariavelTermo v) {
        return new VariavelTermoResponse(v.token(), v.descricao(), v.exemplo(), v.grupo().rotulo());
    }
}
