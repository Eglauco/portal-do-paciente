package com.example.pop.paciente;

/**
 * Nível de acesso do responsável a uma {@link FuncionalidadeApp}. A ausência de
 * registro para uma funcionalidade equivale a {@link #SEM_ACESSO} (padrão).
 */
public enum NivelAcessoResponsavel {
    /** Não acessa a funcionalidade. Padrão quando não há registro. */
    SEM_ACESSO("Sem acesso"),
    /** Visualiza, mas não faz lançamentos. */
    VISUALIZAR("Só visualizar"),
    /** Visualiza e faz lançamentos. */
    VISUALIZAR_LANCAR("Visualizar e lançar");

    private final String descricao;

    NivelAcessoResponsavel(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
