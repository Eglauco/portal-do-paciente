package com.example.pop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import com.example.pop.perfil.PerfilRepository;
import com.example.pop.usuario.UsuarioController;
import com.example.pop.usuario.UsuarioRequest;

/**
 * Usa um usuário de teste próprio (com senha conhecida) para não depender da
 * senha do admin semeado, que pode ser trocada no ambiente de desenvolvimento.
 */
@SpringBootTest
class AuthControllerTest {

    private static final String EMAIL = "auth.controller.test@unidadesaude.com.br";
    private static final String SENHA = "Teste-Auth-123";

    @Autowired
    private AuthController controller;

    @Autowired
    private UsuarioController usuarioController;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private PerfilRepository perfilRepository;

    private Long usuarioId;

    @BeforeEach
    void criarUsuario() {
        Long adminPerfilId = perfilRepository.findByNomeIgnoreCase("Administrador").orElseThrow().getId();
        usuarioId = usuarioController
                .criar(new UsuarioRequest("Auth Controller Test", EMAIL, SENHA, 1L, List.of(adminPerfilId))).getId();
    }

    @AfterEach
    void limpar() {
        usuarioController.excluir(usuarioId);
    }

    @Test
    void loginComCredenciaisValidasEmiteJwt() {
        LoginResponse resposta = controller.login(new LoginRequest(EMAIL, SENHA));
        assertNotNull(resposta.token());
        assertEquals(EMAIL, resposta.email());
        assertNotNull(resposta.unidadeSaudeId(), "o usuário deve ter uma unidade");

        Jwt jwt = jwtDecoder.decode(resposta.token());
        assertEquals(EMAIL, jwt.getSubject());
        assertNotNull(jwt.getClaimAsString("nome"));
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
    }

    @Test
    void loginComSenhaErradaRetorna401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.login(new LoginRequest(EMAIL, "senha-errada")));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void loginComEmailInexistenteRetorna401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.login(new LoginRequest("ninguem@exemplo.com", "qualquer")));
        assertEquals(401, ex.getStatusCode().value());
    }
}
