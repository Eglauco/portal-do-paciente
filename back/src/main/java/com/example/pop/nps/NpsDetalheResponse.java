package com.example.pop.nps;

import java.time.LocalDateTime;
import java.util.List;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.common.Ref;

/** Detalhamento do NPS (inclui os dados do atendimento e as notas por categoria). */
public record NpsDetalheResponse(
        Long id,
        Ref paciente,
        /** Foto (pré-assinada) do paciente para o avatar; null se não tiver. */
        String pacienteFotoUrl,
        Ref unidadeSaude,
        Ref especialidade,
        Ref profissionalSaude,
        Ref procedimento,
        LocalDateTime dataHora,
        StatusNps status,
        String statusDescricao,
        Double media,
        List<CategoriaNotaResponse> notas,
        String observacao,
        LocalDateTime criadoEm,
        LocalDateTime respondidoEm) {

    public static NpsDetalheResponse from(Nps nps) {
        return from(nps, null);
    }

    /** {@code pacienteFotoUrl}: foto pré-assinada do paciente (avatar), ou null. */
    public static NpsDetalheResponse from(Nps nps, String pacienteFotoUrl) {
        Agendamento a = nps.getAgendamento();
        List<CategoriaNotaResponse> notas = nps.getNotasCategorias().stream()
                .sorted((x, y) -> x.getCategoria().getNome().compareToIgnoreCase(y.getCategoria().getNome()))
                .map(CategoriaNotaResponse::from)
                .toList();
        return new NpsDetalheResponse(
                nps.getId(),
                new Ref(a.getPaciente().getId(), a.getPaciente().getNome()),
                pacienteFotoUrl,
                new Ref(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                new Ref(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                new Ref(a.getProfissionalSaude().getId(), a.getProfissionalSaude().getNome()),
                new Ref(a.getProcedimento().getId(), a.getProcedimento().getNome()),
                a.getDataHora(),
                nps.getStatus(),
                nps.getStatus().getDescricao(),
                nps.getMedia(),
                notas,
                nps.getObservacao(),
                nps.getCriadoEm(),
                nps.getRespondidoEm());
    }
}
