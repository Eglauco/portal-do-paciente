package com.example.pop.chat;

/** Tipos de evento registrados na auditoria (log) de uma conversa. */
public enum TipoLogChat {

    VISUALIZOU("Visualizou a conversa"),
    ASSUMIU("Assumiu a conversa"),
    TRANSFERIU("Transferiu a conversa"),
    RESOLVEU("Resolveu a conversa"),
    REABRIU("Reabriu a conversa"),
    STATUS_ALTERADO("Mudança de status");

    private final String descricao;

    TipoLogChat(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
