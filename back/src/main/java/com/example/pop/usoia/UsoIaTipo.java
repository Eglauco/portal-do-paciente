package com.example.pop.usoia;

/** Origem (frente) de um uso de IA registrado no ledger de auditoria. */
public enum UsoIaTipo {

    PRONTUARIO_DOCUMENTO("Análise de documento"),
    PRONTUARIO_RESUMO("Resumo do histórico"),
    CHAT_MENSAGEM("Mensagem do chat"),
    MODERACAO_COMENTARIO("Moderação de comentário");

    private final String descricao;

    UsoIaTipo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
