package com.example.pop.paciente;

/** Tipo de evento na auditoria do cadastro do paciente. */
public enum TipoEventoPaciente {
    CRIACAO("Cadastro criado"),
    ALTERACAO("Cadastro alterado"),
    INATIVACAO("Cadastro inativado"),
    REATIVACAO("Cadastro reativado");

    private final String descricao;

    TipoEventoPaciente(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
