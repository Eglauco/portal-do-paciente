package com.example.pop.paciente;

import jakarta.validation.constraints.NotNull;

/** Corpo para inativar (ativo=false) ou reativar (ativo=true) uma pessoa autorizada pelo app. */
public record MeuResponsavelSituacaoRequest(@NotNull Boolean ativo) {
}
