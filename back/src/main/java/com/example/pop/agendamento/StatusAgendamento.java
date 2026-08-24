package com.example.pop.agendamento;

public enum StatusAgendamento {
    AGUARDANDO_CONFIRMACAO_PACIENTE("Aguardando confirmação do paciente"),
    PACIENTE_CONFIRMOU("Paciente confirmou"),
    CANCELADO_PELA_UNIDADE("Cancelado pela unidade"),
    CANCELADO_PELO_PACIENTE("Cancelado pelo paciente"),
    FALTA_PACIENTE("Falta do paciente"),
    PRESENCA_PACIENTE("Presença do paciente");

    private final String descricao;

    StatusAgendamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
