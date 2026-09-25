package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.util.List;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.common.Ref;

/** Detalhe do prontuário (inclui a lista de documentos e os termos a assinar). */
public record ProntuarioDetalheResponse(
        Long id,
        String numeroAtendimento,
        Long agendamentoId,
        Ref paciente,
        Ref especialidade,
        Ref profissionalSaude,
        Ref unidadeSaude,
        LocalDateTime dataHora,
        List<DocumentoResponse> documentos,
        List<TermoAssinaturaResponse> termos) {

    public static ProntuarioDetalheResponse from(Prontuario p) {
        return from(p, List.of());
    }

    public static ProntuarioDetalheResponse from(Prontuario p, List<TermoAssinatura> termos) {
        Agendamento a = p.getAgendamento();
        return new ProntuarioDetalheResponse(
                p.getId(),
                p.getNumeroAtendimento(),
                a.getId(),
                new Ref(a.getPaciente().getId(), a.getPaciente().getNome()),
                new Ref(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new Ref(a.getProfissionalSaude().getId(), a.getProfissionalSaude().getNome()),
                new Ref(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                a.getDataHora(),
                p.getDocumentos().stream().map(DocumentoResponse::from).toList(),
                termos.stream().map(TermoAssinaturaResponse::from).toList());
    }
}
