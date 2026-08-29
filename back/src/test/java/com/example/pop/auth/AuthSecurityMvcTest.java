package com.example.pop.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.pop.usuario.UsuarioController;
import com.example.pop.usuario.UsuarioRequest;

import jakarta.servlet.Filter;

/**
 * Exercita a cadeia de filtros real do Spring Security (via MockMvc, sem porta/loopback):
 * /auth/login público, /auth/me e /auth/unidade protegidos e o restante da API ainda aberto.
 * Cria seu próprio usuário de teste (senha conhecida) para não depender do admin semeado.
 */
@SpringBootTest
class AuthSecurityMvcTest {

    private static final String EMAIL = "auth.mvc.test@unidadesaude.com.br";
    private static final String SENHA = "Teste-Mvc-123";
    private static final String LOGIN_OK = "{\"email\":\"" + EMAIL + "\",\"senha\":\"" + SENHA + "\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private UsuarioController usuarioController;

    private MockMvc mvc;
    private Long usuarioId;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        usuarioId = usuarioController.criar(new UsuarioRequest("Auth MVC Test", EMAIL, SENHA, 1L)).getId();
    }

    @AfterEach
    void limpar() {
        usuarioController.excluir(usuarioId);
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
        assertTrue(me.getResponse().getContentAsString().contains(EMAIL));
    }

    @Test
    void loginSenhaErradaRetorna401() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDeAdminExigeToken() throws Exception {
        // Fase 4: o back-office agora exige o token do admin. Sem token → 401.
        mvc.perform(get("/usuario").param("size", "1")).andExpect(status().isUnauthorized());

        // Com o token do admin (claim role=ADMIN) → 200.
        MvcResult login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_OK))
                .andExpect(status().isOk()).andReturn();
        String token = login.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/usuario").param("size", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void trocarUnidadeExigeTokenEResponde() throws Exception {
        // Sem token → 401
        mvc.perform(put("/auth/unidade").contentType(MediaType.APPLICATION_JSON).content("{\"unidadeSaudeId\":1}"))
                .andExpect(status().isUnauthorized());

        // Com token → 200 e devolve a unidade escolhida
        MvcResult login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_OK))
                .andExpect(status().isOk()).andReturn();
        String token = login.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        MvcResult troca = mvc.perform(put("/auth/unidade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unidadeSaudeId\":1}"))
                .andExpect(status().isOk()).andReturn();
        assertTrue(troca.getResponse().getContentAsString().contains("\"unidadeSaudeId\":1"));
    }
}
