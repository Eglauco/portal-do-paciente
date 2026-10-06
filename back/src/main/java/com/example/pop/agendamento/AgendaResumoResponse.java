package com.example.pop.agendamento;

import java.time.LocalDate;

/** Linha da LISTA de Agendas (slot + quantos horários/pacientes marcados). */
public record AgendaResumoResponse(
        Long id,
        LocalDate data,
        RefResponse especialidade,
        RefResponse profissionalSaude,
        RefResponse procedimento,
        RefResponse unidadeSaude,
        long totalHorarios) {

    public static AgendaResumoResponse from(Agenda a, long totalHorarios) {
        return new AgendaResumoResponse(
                a.getId(),
                a.getData(),
                new RefResponse(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new RefResponse(a.getProfissionalSaude().getId(), a.getProfissionalSaude().getNome()),
                new RefResponse(a.getProcedimento().getId(), a.getProcedimento().getNome()),
                new RefResponse(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                totalHorarios);
    }
}
