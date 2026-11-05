package com.example.pop.nps;

import java.time.LocalDateTime;

import com.example.pop.agendamento.Horario;
import com.example.pop.common.Ref;

/** Item da listagem de NPS. */
public record NpsResponse(
        Long id,
        com.example.pop.paciente.PacienteRef paciente,
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
        Horario a = nps.getHorario();
        return new NpsResponse(
                nps.getId(),
                com.example.pop.paciente.PacienteRef.de(a.getPaciente()),
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
