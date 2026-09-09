package com.example.pop.notificacaoadmin;

import com.example.pop.perfil.Tela;

/**
 * Tipos de notificação do back-office (sino do admin). Cada tipo exige a TELA
 * correspondente: a notificação só chega a admins cujo perfil libera essa tela
 * (e a unidade do evento), para o clique nunca cair em "sem permissão".
 */
public enum TipoNotificacaoAdmin {
    /** Nova manifestação aberta no SAU. */
    SAU(Tela.SAU),
    /** Comentário da rede social retido para moderação (aguardando aprovar/rejeitar). */
    MODERACAO(Tela.POSTAGENS),
    /** Resposta de NPS com nota baixa (detrator). */
    NPS(Tela.NPS);

    private final Tela telaExigida;

    TipoNotificacaoAdmin(Tela telaExigida) {
        this.telaExigida = telaExigida;
    }

    public Tela getTelaExigida() {
        return telaExigida;
    }
}
