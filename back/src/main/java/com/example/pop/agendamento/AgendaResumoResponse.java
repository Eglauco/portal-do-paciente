package com.example.pop.agendamento;

import java.time.LocalDate;

/** Linha da LISTA de Agendas (slot + quantos horários/pacientes marcados). */
public record AgendaResumoResponse(
        Long id,
        LocalDate data,
        String nome,
        String codigoIntegracao,
        RefResponse especialidade,
        RefResponse profissionalSaude,
        RefResponse configuracaoAgenda,
        RefResponse unidadeSaude,
        long totalHorarios) {

    public static AgendaResumoResponse from(Agenda a, long totalHorarios) {
        return new AgendaResumoResponse(
                a.getId(),
                a.getData(),
                a.getNome(),
                a.getCodigoIntegracao(),
                new RefResponse(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new RefResponse(a.getProfissionalSaude().getId(), a.getProfissionalSaude().getNome()),
                new RefResponse(a.getConfiguracaoAgenda().getId(), a.getConfiguracaoAgenda().getNome()),
                new RefResponse(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                totalHorarios);
    }
}
