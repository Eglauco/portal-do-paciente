package com.example.pop.perfil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.auth.AuthController;
import com.example.pop.auth.LoginRequest;
import com.example.pop.auth.LoginResponse;
import com.example.pop.auth.TrocarUnidadeRequest;
import com.example.pop.usuario.UsuarioController;
import com.example.pop.usuario.UsuarioRequest;

/**
 * CRUD + duplicar de perfil e o cálculo da permissão efetiva (telas + unidades)
 * refletido no login e na troca de unidade. Roda contra o Postgres local.
 */
@SpringBootTest
class PerfilControllerTest {

    @Autowired
    private PerfilController controller;
    @Autowired
    private UsuarioController usuarioController;
    @Autowired
    private AuthController authController;
    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void crudEDuplicar() {
        // Catálogo traz todas as telas do enum.
        assertEquals(Tela.values().length, controller.telas().size());

        PerfilResponse criado = controller.criar(
                new PerfilRequest("Perfil Teste CRUD", EnumSet.of(Tela.SAU, Tela.DASHBOARD), List.of(1L, 2L)));
        Long id = criado.id();
        assertNotNull(id);
        assertTrue(criado.telas().contains("SAU"));
        assertEquals(2, criado.unidades().size());
        assertEquals("Perfil Teste CRUD", controller.buscar(id).getBody().nome());

        // Atualiza: menos telas e uma unidade.
        PerfilResponse atualizado = controller
                .atualizar(id, new PerfilRequest("Perfil Teste CRUD 2", EnumSet.of(Tela.SAU), List.of(1L))).getBody();
        assertEquals("Perfil Teste CRUD 2", atualizado.nome());
        assertEquals(1, atualizado.telas().size());
        assertEquals(1, atualizado.unidades().size());

        // Duplicar copia telas + unidades com o nome "Cópia de ...".
        PerfilResponse copia = controller.duplicar(id).getBody();
        assertEquals("Cópia de Perfil Teste CRUD 2", copia.nome());
        assertEquals(atualizado.telas(), copia.telas());
        assertEquals(1, copia.unidades().size());

        controller.excluir(copia.id());
        controller.excluir(id);
        assertEquals(404, controller.buscar(id).getStatusCode().value());
    }

    @Test
    void permissaoEfetivaNoLoginETrocaDeUnidade() {
        // Perfil que só enxerga a unidade 2 e libera só a tela SAU.
        PerfilResponse perfil = controller
                .criar(new PerfilRequest("Perfil Restrito", EnumSet.of(Tela.SAU), List.of(2L)));
        String email = "perfil.rbac.test@unidadesaude.com.br";
        // A unidade ativa (2) precisa estar entre as unidades do perfil.
        Long uid = usuarioController
                .criar(new UsuarioRequest("RBAC Test", email, "segredo123", 2L, List.of(perfil.id()))).getId();
        try {
            LoginResponse login = authController.login(new LoginRequest(email, "segredo123"));
            // Telas efetivas = as do perfil.
            assertEquals(List.of("SAU"), login.telas());
            // Só enxerga a unidade 2 (a acessível do perfil).
            assertEquals(2L, login.unidadeSaudeId());
            assertEquals(1, login.unidades().size());
            assertEquals(2L, login.unidades().get(0).id());

            Jwt jwt = jwtDecoder.decode(login.token());
            // Trocar para uma unidade fora do perfil → 403.
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> authController.trocarUnidade(jwt, new TrocarUnidadeRequest(1L)));
            assertEquals(403, ex.getStatusCode().value());
            // Trocar para a unidade do perfil → ok.
            assertEquals(2L, authController.trocarUnidade(jwt, new TrocarUnidadeRequest(2L)).unidadeSaudeId());
        } finally {
            usuarioController.excluir(uid);
            controller.excluir(perfil.id());
        }
        assertFalse(controller.telas().isEmpty());
    }

    @Test
    void listaPaginadaComFiltroPorNome() {
        String nomeUnico = "Perfil Filtro XYZ-" + System.nanoTime();
        PerfilResponse perfil = controller.criar(new PerfilRequest(nomeUnico, EnumSet.of(Tela.SAU), List.of(1L)));
        try {
            var pagina = controller.listar(null, "Filtro XYZ", 0, 10);
            assertEquals(1, pagina.totalElements());
            assertEquals(nomeUnico, pagina.content().get(0).nome());
            // Filtro por código (id) também retorna o registro.
            assertEquals(1, controller.listar(perfil.id(), null, 0, 10).totalElements());
        } finally {
            controller.excluir(perfil.id());
        }
    }

    @Test
    void naoExcluiPerfilEmUso() {
        PerfilResponse perfil = controller
                .criar(new PerfilRequest("Perfil Em Uso", EnumSet.of(Tela.SAU), List.of(2L)));
        String email = "perfil.emuso.test@unidadesaude.com.br";
        Long uid = usuarioController
                .criar(new UsuarioRequest("Em Uso Test", email, "segredo123", 2L, List.of(perfil.id()))).getId();
        try {
            // Perfil vinculado a um usuário → 409.
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.excluir(perfil.id()));
            assertEquals(409, ex.getStatusCode().value());
        } finally {
            usuarioController.excluir(uid);
        }
        // Sem vínculo, agora exclui normalmente.
        assertEquals(204, controller.excluir(perfil.id()).getStatusCode().value());
    }
}
