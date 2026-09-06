package com.example.pop.pacienteauth;

import jakarta.validation.constraints.NotNull;

/** Perfil (paciente) escolhido na tela "Selecionar Perfil". */
public record TrocarPerfilRequest(@NotNull Long pacienteId) {
}
