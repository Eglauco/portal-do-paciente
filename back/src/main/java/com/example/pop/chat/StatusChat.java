package com.example.pop.chat;

public enum StatusChat {
    /** Paciente enviou a última mensagem e a unidade ainda não visualizou. */
    NAO_LIDA("Não lida"),
    /** Unidade visualizou, mas ainda não respondeu. */
    AGUARDANDO_RESPOSTA("Aguardando resposta"),
    /** Conversa em andamento com um ATENDENTE HUMANO (última resposta foi da unidade). */
    EM_ATENDIMENTO("Em atendimento"),
    /** Conversa em andamento com a ASSISTENTE VIRTUAL (IA) — nenhum humano assumiu ainda. */
    ATENDIMENTO_IA("Atendimento com IA"),
    /** Conversa encerrada/resolvida. */
    RESOLVIDO("Resolvido");

    private final String descricao;

    StatusChat(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
