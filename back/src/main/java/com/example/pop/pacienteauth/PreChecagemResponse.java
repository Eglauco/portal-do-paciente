package com.example.pop.pacienteauth;

/** Resultado da pré-checagem: a identidade central do CPF já tem senha (PIN) definida? */
public record PreChecagemResponse(boolean temSenha) {
}
