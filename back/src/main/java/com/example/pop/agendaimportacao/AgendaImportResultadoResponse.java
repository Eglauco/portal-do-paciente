package com.example.pop.agendaimportacao;

/** Resultado da confirmação da importação (Fase 2): a agenda criada e quantos horários foram gravados. */
public record AgendaImportResultadoResponse(Long agendaId, int totalHorarios) {
}
