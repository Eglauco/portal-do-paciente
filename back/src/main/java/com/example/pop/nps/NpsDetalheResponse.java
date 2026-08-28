package com.example.pop.nps;

import java.time.LocalDateTime;
import java.util.List;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.common.Ref;

/** Detalhamento do NPS (inclui os dados do atendimento e as notas por categoria). */
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
        Double media,
        List<CategoriaNotaResponse> notas,
        String observacao,
        LocalDateTime criadoEm,
        LocalDateTime respondidoEm) {

    public static NpsDetalheResponse from(Nps nps) {
        Agendamento a = nps.getAgendamento();
        List<CategoriaNotaResponse> notas = nps.getNotasCategorias().stream()
                .sorted((x, y) -> x.getCategoria().getNome().compareToIgnoreCase(y.getCategoria().getNome()))
                .map(CategoriaNotaResponse::from)
                .toList();
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
                nps.getMedia(),
                notas,
                nps.getObservacao(),
                nps.getCriadoEm(),
                nps.getRespondidoEm());
    }
}
