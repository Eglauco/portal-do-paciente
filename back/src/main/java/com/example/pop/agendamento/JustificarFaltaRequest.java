package com.example.pop.agendamento;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** Justificativa da falta enviada pelo paciente (app): motivos selecionados + texto livre. */
public record JustificarFaltaRequest(
        @NotEmpty List<Long> motivoIds,
        String justificativa) {
}
