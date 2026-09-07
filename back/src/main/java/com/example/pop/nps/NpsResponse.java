package com.example.pop.nps;

import java.time.LocalDateTime;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.common.Ref;

/** Item da listagem de NPS. */
public record NpsResponse(
        Long id,
        Ref paciente,
        Ref unidadeSaude,
        Ref especialidade,
        LocalDateTime dataHora,
        StatusNps status,
        String statusDescricao,
        Double media,
        /** Nome do responsável que respondeu pelo paciente; null quando foi o próprio. */
        String responsavelNome,
        LocalDateTime criadoEm) {

    public static NpsResponse from(Nps nps) {
        return from(nps, null);
    }

    public static NpsResponse from(Nps nps, String responsavelNome) {
        Agendamento a = nps.getAgendamento();
        return new NpsResponse(
                nps.getId(),
                new Ref(a.getPaciente().getId(), a.getPaciente().getNome()),
                new Ref(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                new Ref(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                a.getDataHora(),
                nps.getStatus(),
                nps.getStatus().getDescricao(),
                nps.getMedia(),
                responsavelNome,
                nps.getCriadoEm());
    }
}
