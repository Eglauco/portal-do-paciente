package com.example.pop.paciente;

/**
 * Funcionalidades do app sujeitas ao controle de permissão do responsável (quando
 * ele age por um perfil dependente). Cada uma recebe um {@link NivelAcessoResponsavel}
 * por responsável. As travas de fato no app usam este mesmo enum.
 */
public enum FuncionalidadeApp {
    AGENDAMENTOS("Agendamentos"),
    CHAT("Chat"),
    SAU("SAU (Manifestações)"),
    REDE_SOCIAL("Rede Social"),
    MEU_PERFIL("Meu Perfil"),
    PRONTUARIO("Prontuário"),
    NPS("NPS");

    private final String descricao;

    FuncionalidadeApp(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
