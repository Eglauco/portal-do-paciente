package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.util.List;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.common.Ref;

/** Detalhe do prontuário no BACK-OFFICE: documentos com o resultado da análise por IA. */
public record ProntuarioAdminDetalheResponse(
        Long id,
        String numeroAtendimento,
        Long agendamentoId,
        Ref paciente,
        Ref especialidade,
        Ref profissionalSaude,
        Ref unidadeSaude,
        LocalDateTime dataHora,
        StatusAlertaProntuario statusAlerta,
        String statusAlertaDescricao,
        List<DocumentoAdminResponse> documentos) {

    public static ProntuarioAdminDetalheResponse from(Prontuario p) {
        Agendamento a = p.getAgendamento();
        return new ProntuarioAdminDetalheResponse(
                p.getId(),
                p.getNumeroAtendimento(),
                a.getId(),
                new Ref(a.getPaciente().getId(), a.getPaciente().getNome()),
                new Ref(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new Ref(a.getProfissionalSaude().getId(), a.getProfissionalSaude().getNome()),
                new Ref(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                a.getDataHora(),
                p.getStatusAlerta(),
                p.getStatusAlerta() == null ? null : p.getStatusAlerta().getDescricao(),
                p.getDocumentos().stream().map(DocumentoAdminResponse::from).toList());
    }
}
