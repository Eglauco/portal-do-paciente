package com.example.pop.agendamento;

import java.time.LocalDateTime;

public record AgendamentoResponse(
        Long id,
        LocalDateTime dataHora,
        RefResponse especialidade,
        RefResponse profissionalSaude,
        RefResponse procedimento,
        RefResponse paciente,
        RefResponse unidadeSaude,
        StatusAgendamento statusAgendamento,
        String statusDescricao) {

    public static AgendamentoResponse from(Agendamento a) {
        return new AgendamentoResponse(
                a.getId(),
                a.getDataHora(),
                new RefResponse(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new RefResponse(a.getProfissionalSaude().getId(), a.getProfissionalSaude().getNome()),
                new RefResponse(a.getProcedimento().getId(), a.getProcedimento().getNome()),
                new RefResponse(a.getPaciente().getId(), a.getPaciente().getNome()),
                new RefResponse(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                a.getStatusAgendamento(),
                a.getStatusAgendamento().getDescricao());
    }
}
