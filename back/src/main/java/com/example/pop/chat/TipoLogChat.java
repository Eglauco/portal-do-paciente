package com.example.pop.chat;

/** Tipos de evento registrados na auditoria (log) de uma conversa. */
public enum TipoLogChat {

    VISUALIZOU("Visualizou a conversa"),
    ASSUMIU("Assumiu a conversa"),
    TRANSFERIU("Transferiu a conversa"),
    RESOLVEU("Resolveu a conversa"),
    REABRIU("Reabriu a conversa"),
    STATUS_ALTERADO("Mudança de status"),
    RESPONDEU_IA("Assistente virtual respondeu"),
    ESCALOU_IA("Assistente virtual encaminhou para atendente"),
    RESOLVEU_IA("Assistente virtual resolveu a conversa");

    private final String descricao;

    TipoLogChat(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
