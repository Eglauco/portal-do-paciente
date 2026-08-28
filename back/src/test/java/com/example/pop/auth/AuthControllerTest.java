package com.example.pop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class AuthControllerTest {

    private static final String EMAIL_ADMIN = "adm@unidadesaude.com.br";
    private static final String SENHA_ADMIN = "Admin@123";

    @Autowired
    private AuthController controller;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void loginComCredenciaisValidasEmiteJwt() {
        LoginResponse resposta = controller.login(new LoginRequest(EMAIL_ADMIN, SENHA_ADMIN));
        assertNotNull(resposta.token());
        assertEquals(EMAIL_ADMIN, resposta.email());

        // O token é válido e carrega o e-mail no subject + o nome como claim.
        Jwt jwt = jwtDecoder.decode(resposta.token());
        assertEquals(EMAIL_ADMIN, jwt.getSubject());
        assertNotNull(jwt.getClaimAsString("nome"));
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
    }

    @Test
    void loginComSenhaErradaRetorna401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.login(new LoginRequest(EMAIL_ADMIN, "senha-errada")));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void loginComEmailInexistenteRetorna401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.login(new LoginRequest("ninguem@exemplo.com", "qualquer")));
        assertEquals(401, ex.getStatusCode().value());
    }
}
