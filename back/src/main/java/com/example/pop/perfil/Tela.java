package com.example.pop.perfil;

/**
 * Catálogo de telas (itens de menu) que um perfil pode liberar. A liberação é por
 * tela inteira. O nome é a chave usada no token/front; a descrição é o rótulo do menu.
 */
public enum Tela {
    DASHBOARD("Dashboard"),
    AGENDAMENTOS("Agendamentos"),
    CHATS("Chats ao vivo"),
    SAU("SAU"),
    TIPOS_MANIFESTACAO("Tipos de Manifestação"),
    NPS("NPS"),
    CATEGORIAS_NPS("Categorias de NPS"),
    PACIENTES("Pacientes"),
    PRONTUARIOS("Prontuários"),
    POSTAGENS("Rede Social"),
    ESPECIALIDADES("Especialidades"),
    PROFISSIONAIS("Profissionais"),
    PROCEDIMENTOS("Procedimentos"),
    MOTIVOS_FALTA("Motivos de falta"),
    UNIDADES("Unidades"),
    USUARIOS("Usuários"),
    PERFIS("Perfis"),
    CONFIGURACOES("Configurações");

    private final String descricao;

    Tela(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
