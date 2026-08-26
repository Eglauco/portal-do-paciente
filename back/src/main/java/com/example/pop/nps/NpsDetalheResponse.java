package com.example.pop.nps;

import java.time.LocalDateTime;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.common.Ref;

/** Detalhamento do NPS (inclui os dados do atendimento). */
public record NpsDetalheResponse(
        Long id,
        Ref paciente,
        Ref unidadeSaude,
        Ref especialidade,
        Ref profissionalSaude,
        Ref procedimento,
        LocalDateTime dataHora,
        StatusNps status,
        String statusDescricao,
        Integer nota,
        String observacao,
        LocalDateTime criadoEm,
        LocalDateTime respondidoEm) {

    public static NpsDetalheResponse from(Nps nps) {
        Agendamento a = nps.getAgendamento();
        return new NpsDetalheResponse(
                nps.getId(),
                new Ref(a.getPaciente().getId(), a.getPaciente().getNome()),
                new Ref(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                new Ref(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new Ref(a.getProfissionalSaude().getId(), a.getProfissionalSaude().getNome()),
                new Ref(a.getProcedimento().getId(), a.getProcedimento().getNome()),
                a.getDataHora(),
                nps.getStatus(),
                nps.getStatus().getDescricao(),
                nps.getNota(),
                nps.getObservacao(),
                nps.getCriadoEm(),
                nps.getRespondidoEm());
    }
}
