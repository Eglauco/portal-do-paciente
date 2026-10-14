package com.example.pop.pacienteauth;

import jakarta.validation.constraints.NotBlank;

/**
 * Pré-checagem do login (só CPF): pergunta se o CPF já tem uma senha (PIN) CENTRAL na plataforma, para
 * o app rotear — tela de senha (tem) ou primeiro acesso (não tem). É CLÍNICO-AGNÓSTICO (lê a identidade
 * central no {@code public}), então NÃO revela se o CPF é paciente DESTA clínica (minimiza a enumeração).
 */
public record PreChecagemRequest(@NotBlank String cpf) {
}
