package com.example.pop.pacienteauth;

import jakarta.validation.constraints.NotBlank;

/**
 * Login por senha (sem SMS): CPF + PIN + id do aparelho. O PIN é a prova de identidade; o CPF
 * apenas identifica a conta (telefone e data de nascimento não entram neste caminho — é o login
 * rápido de quem já ativou e definiu a senha).
 */
public record LoginSenhaRequest(
        @NotBlank String cpf,
        @NotBlank String senha,
        @NotBlank String dispositivoId,
        /**
         * Clínica escolhida pelo app COMPARTILHADO na tela "Selecionar Clínica" (2ª chamada, quando o CPF
         * está em mais de uma). Nulo na 1ª chamada e no app A-baked (a clínica vem do X-Inquilino assado).
         */
        Long inquilinoId) {
}
