package com.example.pop.notificacao;

/**
 * Tipos de notificação guardados para o paciente ver na tela "Notificações".
 * (Chat fica de fora de propósito — tem tela e tempo real próprios.) O nome do
 * tipo também guia a navegação no app ao tocar na notificação.
 */
public enum TipoNotificacao {
    AGENDAMENTO,
    FALTA,
    NPS,
    POSTAGEM,
    PRONTUARIO,
    SAU,
    LEMBRETE
}
