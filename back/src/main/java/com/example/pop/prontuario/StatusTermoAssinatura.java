package com.example.pop.prontuario;

/** Situação de um termo (TCLE) a assinar dentro de um prontuário. */
public enum StatusTermoAssinatura {
    PENDENTE("Pendente de assinatura"),
    /** Assinado na cerimônia (evento do app); aguardando o webhook doc_signed confirmar. */
    EM_CONFIRMACAO("Assinatura em confirmação"),
    ASSINADO("Assinado"),
    /** Falha do lado da ZapSign (recusa); o paciente precisa refazer a assinatura. */
    TENTAR_NOVAMENTE("Tentar novamente"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusTermoAssinatura(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
