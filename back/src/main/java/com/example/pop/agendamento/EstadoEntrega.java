package com.example.pop.agendamento;

/**
 * Estado da ENTREGA da notificação de um agendamento a um destinatário (Fase 1). Aferido
 * no momento do disparo; não é prova de que a pessoa viu, e sim de que havia (ou não)
 * aparelho e de que a Expo aceitou (ou não) o envio.
 */
public enum EstadoEntrega {
    /** A pessoa não tem nenhum aparelho com push registrado (sem app / nunca logou / sem permissão). */
    PACIENTE_SEM_APLICATIVO("Sem aplicativo"),
    /** A Expo aceitou o envio para ao menos um aparelho da pessoa (aguardando confirmação de entrega). */
    NOTIFICACAO_ENVIADA("Notificação enviada"),
    /** A Expo confirmou a entrega ao aparelho (FCM/APNs) — receipt "ok" em ao menos um aparelho. */
    NOTIFICACAO_ENTREGUE("Notificação entregue"),
    /** A pessoa tinha aparelho(s), mas o envio/entrega não foi aceito (token inválido/removido ou falha). */
    SEM_NOTIFICACAO_ATIVA("Sem notificação ativa");

    private final String descricao;

    EstadoEntrega(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
