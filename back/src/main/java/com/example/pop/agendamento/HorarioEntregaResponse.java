package com.example.pop.agendamento;

import java.time.LocalDateTime;

/**
 * Um evento de entrega da notificação de um agendamento a um destinatário. A tabela é
 * append-only: cada mudança de estado é um novo registro, então o front agrupa por
 * pessoa ({@code tipo} + {@code responsavelId}) e monta a linha do tempo por {@code criadoEm}.
 */
public record HorarioEntregaResponse(
        Long id,
        TipoDestinatario tipo,
        Long responsavelId,
        String nome,
        String telefone,
        EstadoEntrega estado,
        String estadoDescricao,
        LocalDateTime criadoEm) {

    static HorarioEntregaResponse from(HorarioEntrega e) {
        return new HorarioEntregaResponse(e.getId(), e.getTipo(), e.getResponsavelId(), e.getNome(),
                e.getTelefone(), e.getEstado(), e.getEstado().getDescricao(), e.getCriadoEm());
    }
}
