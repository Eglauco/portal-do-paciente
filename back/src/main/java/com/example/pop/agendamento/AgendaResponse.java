package com.example.pop.agendamento;

import java.time.LocalDate;
import java.util.List;

/** Detalhe de uma Agenda: o slot + a lista de Horários (pacientes marcados). */
public record AgendaResponse(
        Long id,
        LocalDate data,
        String nome,
        String codigoIntegracao,
        RefResponse especialidade,
        RefResponse profissionalSaude,
        RefResponse configuracaoAgenda,
        RefResponse unidadeSaude,
        List<HorarioResponse> horarios) {

    public static AgendaResponse from(Agenda a, List<Horario> horarios) {
        return new AgendaResponse(
                a.getId(),
                a.getData(),
                a.getNome(),
                a.getCodigoIntegracao(),
                new RefResponse(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new RefResponse(a.getProfissionalSaude().getId(), a.getProfissionalSaude().getNome()),
                new RefResponse(a.getConfiguracaoAgenda().getId(), a.getConfiguracaoAgenda().getNome()),
                new RefResponse(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                horarios.stream().map(HorarioResponse::from).toList());
    }
}
