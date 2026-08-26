package com.example.pop.prontuario;

import java.time.LocalDateTime;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.common.Ref;

/** Item da listagem de prontuários. */
public record ProntuarioResponse(
        Long id,
        String numeroAtendimento,
        Long agendamentoId,
        Ref paciente,
        Ref especialidade,
        Ref unidadeSaude,
        LocalDateTime dataHora,
        int documentos) {

    public static ProntuarioResponse from(Prontuario p) {
        Agendamento a = p.getAgendamento();
        return new ProntuarioResponse(
                p.getId(),
                p.getNumeroAtendimento(),
                a.getId(),
                new Ref(a.getPaciente().getId(), a.getPaciente().getNome()),
                new Ref(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new Ref(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                a.getDataHora(),
                p.getDocumentos().size());
    }
}
