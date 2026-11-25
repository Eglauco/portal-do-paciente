package com.example.pop.agendamento;

/**
 * Evento registrado no histórico do horário que NÃO é troca de status (ver {@link HorarioLog#getEvento()}).
 * Hoje: as ações manuais de convite do app feitas pelo atendente.
 */
public enum EventoLogHorario {

    /** O atendente enviou o convite do app por SMS (o provedor aceitou o envio). */
    CONVITE_SMS("Convite do app enviado por SMS"),

    /** O atendente abriu o WhatsApp com o convite pronto (não dá para saber se a mensagem foi de fato enviada). */
    CONVITE_WHATSAPP("Convite do app aberto no WhatsApp");

    private final String descricao;

    EventoLogHorario(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
