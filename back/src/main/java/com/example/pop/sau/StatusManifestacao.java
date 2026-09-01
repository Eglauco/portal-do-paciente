package com.example.pop.sau;

/** Ciclo de vida da manifestação no SAU. */
public enum StatusManifestacao {
    AGUARDANDO_SAU("Aguardando SAU responder"),
    AGUARDANDO_PACIENTE("Aguardando paciente responder"),
    FECHADA("Mensagem fechada");

    private final String descricao;

    StatusManifestacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
