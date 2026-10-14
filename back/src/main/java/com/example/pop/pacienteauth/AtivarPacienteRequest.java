package com.example.pop.pacienteauth;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Ativação do app: telefone + CPF + data de nascimento (identidade) + código do OTP + id do aparelho. */
public record AtivarPacienteRequest(
        @NotBlank String cpf,
        @NotNull LocalDate dataNascimento,
        @NotBlank String codigo,
        @NotBlank String dispositivoId,
        @NotBlank String telefone,
        /**
         * Clínica escolhida pelo app COMPARTILHADO (quando a identidade do CPF confere em mais de uma).
         * Nula no app A-baked (vem do X-Inquilino assado) e quando só há uma clínica (o backend resolve).
         */
        Long inquilinoId) {

    /** Compat (app A-baked / testes): ativação sem clínica escolhida — {@code inquilinoId} nulo. */
    public AtivarPacienteRequest(String cpf, LocalDate dataNascimento, String codigo, String dispositivoId,
            String telefone) {
        this(cpf, dataNascimento, codigo, dispositivoId, telefone, null);
    }
}
