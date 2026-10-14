package com.example.pop.pacienteauth;

import jakarta.validation.constraints.NotNull;

/** Troca da clínica da sessão (app compartilhado): a clínica alvo, que deve ser uma das do próprio CPF. */
public record TrocarClinicaRequest(@NotNull Long inquilinoId) {
}
