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
        Integer nota,
        String classificacao,
        LocalDateTime criadoEm) {

    public static NpsResponse from(Nps nps) {
        Agendamento a = nps.getAgendamento();
        return new NpsResponse(
                nps.getId(),
                new Ref(a.getPaciente().getId(), a.getPaciente().getNome()),
                new Ref(a.getUnidadeSaude().getId(), a.getUnidadeSaude().getNome()),
                new Ref(a.getEspecialidade().getId(), a.getEspecialidade().getNome()),
                a.getDataHora(),
                nps.getStatus(),
                nps.getStatus().getDescricao(),
                nps.getNota(),
                classificacao(nps.getNota()),
                nps.getCriadoEm());
    }

    /** Classificação NPS a partir da nota (nula se ainda não respondido). */
    public static String classificacao(Integer nota) {
        if (nota == null) {
            return null;
        }
        if (nota >= 9) {
            return "Promotor";
        }
        if (nota >= 7) {
            return "Neutro";
        }
        return "Detrator";
    }
}
