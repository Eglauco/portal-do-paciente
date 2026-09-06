package com.example.pop.pacienteauth;

import java.util.List;

/**
 * Resposta da ativação por OTP: um token já apontando para um perfil padrão
 * (o próprio, ou o primeiro dependente) e a lista de perfis para o app abrir a
 * tela "Selecionar Perfil". Os campos token/pacienteId/nome espelham a sessão
 * do perfil padrão.
 */
public record AtivarResponse(String token, Long pacienteId, String nome, List<PerfilResponse> perfis) {
}
