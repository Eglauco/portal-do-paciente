package com.example.pop.paciente;

/** Sexo do paciente (cadastro). O nome é a chave; a descrição é o rótulo exibido. */
public enum Sexo {
    MASCULINO("Masculino"),
    FEMININO("Feminino"),
    OUTRO("Outro"),
    NAO_INFORMADO("Prefiro não informar");

    private final String descricao;

    Sexo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
