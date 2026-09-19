package com.example.pop.prontuario;

/** Status de alerta do prontuário (rollup dos documentos), para busca/filtro no back-office. */
public enum StatusAlertaProntuario {

    /** Nenhum documento gerou alerta. */
    SEM_ALTERACOES("Sem alterações"),
    /** Ao menos um documento com alerta ainda não validado (urgente). */
    AGUARDANDO_VALIDACAO("Aguardando validação"),
    /** Teve alerta e todos já foram validados. */
    VALIDADO("Validado");

    private final String descricao;

    StatusAlertaProntuario(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
