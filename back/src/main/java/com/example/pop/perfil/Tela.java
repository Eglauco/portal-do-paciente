package com.example.pop.perfil;

/**
 * Catálogo de telas (itens de menu) que um perfil pode liberar. A liberação é por
 * tela inteira. O nome é a chave usada no token/front; a descrição é o rótulo do menu.
 *
 * <p>Os dashboards são liberados um a um (um perfil pode ver só alguns): cada um é uma
 * tela {@code DASHBOARD_*} própria. O grupo "Dashboard" no menu aparece se o perfil tiver
 * pelo menos um deles.
 */
public enum Tela {
    DASHBOARD_GERAL("Dashboard · Visão geral"),
    DASHBOARD_AGENDAMENTOS("Dashboard · Agendamentos"),
    DASHBOARD_CHATS("Dashboard · Chats ao vivo"),
    DASHBOARD_SAU("Dashboard · SAU"),
    DASHBOARD_NPS("Dashboard · NPS"),
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
