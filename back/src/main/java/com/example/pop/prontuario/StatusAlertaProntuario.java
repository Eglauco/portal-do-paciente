package com.example.pop.prontuario;

/** Status de alerta do prontuário (rollup dos documentos), para busca/filtro no back-office. */
public enum StatusAlertaProntuario {

    /** Nenhum documento com alerta pendente ou alteração confirmada. */
    SEM_ALTERACOES("Sem alterações"),
    /** Ao menos um documento com alerta ainda aguardando decisão humana (urgente). */
    AGUARDANDO_VALIDACAO("Aguardando validação"),
    /** Sem pendências e ao menos um documento com alteração confirmada por um humano. */
    ALTERACAO_CONFIRMADA("Alteração confirmada");

    private final String descricao;

    StatusAlertaProntuario(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
