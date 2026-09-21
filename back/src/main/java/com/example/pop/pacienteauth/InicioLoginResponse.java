package com.example.pop.pacienteauth;

/** Se a conta já tem senha (mostrar o campo de senha) e se o login por senha está bloqueado (usar SMS). */
public record InicioLoginResponse(boolean temSenha, boolean bloqueada) {
}
