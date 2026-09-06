package com.example.pop.pacienteauth;

import java.util.Map;

import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.NivelAcessoResponsavel;

/**
 * Um perfil que a conta (telefone) pode acessar na tela "Selecionar Perfil".
 * {@code proprio} = é o próprio paciente do telefone; senão é um dependente.
 * {@code fotoUrl} vem pré-assinada (ou null). {@code permissoes} traz o nível de
 * acesso por funcionalidade quando é um dependente (para o app aplicar as travas);
 * vazio no perfil próprio (acesso total).
 */
public record PerfilResponse(Long pacienteId, String nome, String fotoUrl, boolean proprio,
        Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes) {
}
