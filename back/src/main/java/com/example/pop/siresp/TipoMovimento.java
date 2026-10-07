package com.example.pop.siresp;

/**
 * Movimentação da mensagem do SIRESP/CROSS (campo {@code TIPO_CONSULTA}/{@code TIPO_EXAME}): {@code A}=
 * {@link #AGENDAMENTO} (cria), {@code C}={@link #CANCELAMENTO} (cancela o horário existente), {@code T}=
 * {@link #TRANSFERENCIA} (cancela o horário de origem e cria o novo). Vale para consulta e exame.
 */
public enum TipoMovimento {

    AGENDAMENTO("Agendamento"),
    CANCELAMENTO("Cancelamento"),
    TRANSFERENCIA("Transferência");

    private final String descricao;

    TipoMovimento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
