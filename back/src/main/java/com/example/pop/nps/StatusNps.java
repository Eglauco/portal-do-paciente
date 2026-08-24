package com.example.pop.nps;

public enum StatusNps {
    /** Gerado automaticamente, aguardando o paciente responder. */
    PENDENTE("Aguardando resposta"),
    /** Paciente respondeu (nota registrada). */
    RESPONDIDO("Respondido"),
    /** Não respondido dentro do prazo. */
    EXPIRADO("Expirado");

    private final String descricao;

    StatusNps(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
