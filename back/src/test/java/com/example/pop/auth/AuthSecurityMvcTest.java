package com.example.pop.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;

/**
 * Exercita a cadeia de filtros real do Spring Security (via MockMvc, sem porta/loopback):
 * /auth/login público, /auth/me protegido e o restante da API ainda aberto.
 */
@SpringBootTest
class AuthSecurityMvcTest {

    private static final String LOGIN_OK = "{\"email\":\"adm@unidadesaude.com.br\",\"senha\":\"Admin@123\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    void loginPublicoEmiteToken_eMeExigeAutenticacao() throws Exception {
        MvcResult res = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_OK))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        assertTrue(body.contains("\"token\""), "a resposta do login deve conter o token");
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertFalse(token.isBlank());

        // /auth/me sem token → 401
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

        // /auth/me com token válido → 200 e traz o e-mail
        MvcResult me = mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(me.getResponse().getContentAsString().contains("adm@unidadesaude.com.br"));
    }

    @Test
    void loginSenhaErradaRetorna401() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"adm@unidadesaude.com.br\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiPermaneceAbertaSemToken() throws Exception {
        // Recorte combinado: o restante da API segue acessível sem token por ora.
        mvc.perform(get("/usuario").param("size", "1")).andExpect(status().isOk());
    }
}
