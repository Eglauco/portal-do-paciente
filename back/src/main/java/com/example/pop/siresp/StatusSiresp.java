package com.example.pop.siresp;

/**
 * Status de um registro do SIRESP, derivado da criação do agendamento (sem coluna própria — sempre consistente):
 * {@link #AGENDADO} quando já gerou agendamento; {@link #REVISAO} quando ainda faltam informações (precisa que o
 * usuário corrija os códigos de integração e reprocesse).
 */
public enum StatusSiresp {

    AGENDADO("Agendado com sucesso"),
    REVISAO("Precisa de revisão");

    private final String descricao;

    StatusSiresp(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Descrição do RESULTADO considerando a movimentação: concluído vira "Agendado"/"Cancelado"/"Transferido com
     * sucesso" conforme o tipo; pendente continua "Precisa de revisão".
     */
    public String descricao(TipoMovimento movimento) {
        if (this == REVISAO) {
            return descricao;
        }
        return switch (movimento) {
            case CANCELAMENTO -> "Cancelado com sucesso";
            case TRANSFERENCIA -> "Transferido com sucesso";
            default -> "Agendado com sucesso";
        };
    }

    /** Deriva o status do registro: AGENDADO (concluído) se já há horário vinculado; senão REVISAO. */
    public static StatusSiresp de(Siresp s) {
        return s.getAgendamentoId() != null ? AGENDADO : REVISAO;
    }
}
