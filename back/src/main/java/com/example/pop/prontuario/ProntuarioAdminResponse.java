package com.example.pop.prontuario;

import java.time.LocalDateTime;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.common.Ref;

/** Item da listagem de prontuários no BACK-OFFICE (inclui o status de alerta, para filtro). */
public record ProntuarioAdminResponse(
        Long id,
        String numeroAtendimento,
        Long agendamentoId,
        Ref paciente,
        Ref especialidade,
        Ref unidadeSaude,
        LocalDateTime dataHora,
        int documentos,
        StatusAlertaProntuario statusAlerta,
        String statusAlertaDescricao) {

    public static ProntuarioAdminResponse from(Prontuario p) {
        Agendamento a = p.getAgendamento();
        return new ProntuarioAdminResponse(
                p.getId(),
                p.getNumeroAtendimento(),
                a.getId(),
                new Ref(a.getPaciente().getId(), a.getPaciente().getNome()),
                new Ref(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new Ref(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                a.getDataHora(),
                p.getDocumentos().size(),
                p.getStatusAlerta(),
                p.getStatusAlerta() == null ? null : p.getStatusAlerta().getDescricao());
    }
}
