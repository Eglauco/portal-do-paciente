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

    /** Deriva o status do registro: AGENDADO se já há agendamento gerado; senão REVISAO. */
    public static StatusSiresp de(Siresp s) {
        return s.getAgendamentoId() != null ? AGENDADO : REVISAO;
    }
}
