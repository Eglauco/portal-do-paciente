package com.example.pop.agendamento;

import java.time.LocalDateTime;

public record AgendamentoResponse(
        Long id,
        LocalDateTime dataHora,
        String especialidade,
        String profissionalSaude,
        RefResponse paciente,
        RefResponse unidadeSaude,
        StatusAgendamento statusAgendamento,
        String statusDescricao) {

    public static AgendamentoResponse from(Agendamento a) {
        return new AgendamentoResponse(
                a.getId(),
                a.getDataHora(),
                a.getEspecialidade(),
                a.getProfissionalSaude(),
                new RefResponse(a.getPaciente().getId(), a.getPaciente().getNome()),
                new RefResponse(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                a.getStatusAgendamento(),
                a.getStatusAgendamento().getDescricao());
    }
}
