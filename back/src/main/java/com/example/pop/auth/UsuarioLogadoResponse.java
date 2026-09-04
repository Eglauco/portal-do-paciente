package com.example.pop.auth;

import java.util.List;

import com.example.pop.common.Ref;
import com.example.pop.unidade.Unidade;
import com.example.pop.usuario.Usuario;

/** Dados do usuário autenticado (GET /auth/me, PUT /auth/unidade). */
public record UsuarioLogadoResponse(
        String nome,
        String email,
        Long unidadeSaudeId,
        String unidadeSaudeNome,
        List<String> telas,
        List<Ref> unidades) {

    public static UsuarioLogadoResponse from(Usuario u) {
        Unidade ativa = Permissoes.unidadeAtivaEfetiva(u);
        return new UsuarioLogadoResponse(
                u.getNome(),
                u.getEmail(),
                ativa == null ? null : ativa.getId(),
                ativa == null ? null : ativa.getNome(),
                Permissoes.telas(u),
                Permissoes.unidades(u));
    }
}
