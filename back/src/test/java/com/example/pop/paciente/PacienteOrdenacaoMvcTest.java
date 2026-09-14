package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

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

import com.example.pop.perfil.PerfilRepository;
import com.example.pop.usuario.UsuarioController;
import com.example.pop.usuario.UsuarioRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;

/**
 * Exercita a ordenação por coluna pela camada HTTP REAL (MockMvc). O bug do separador só
 * aparece no binding do {@code @RequestParam List<String>} — chamar o controller direto NÃO
 * pega (o teste unitário recebe a lista já pronta). Aqui "codigo:desc" passa pelo Spring
 * (uma única ocorrência não é quebrada, por não usar vírgula) e deve voltar decrescente.
 */
@SpringBootTest
class PacienteOrdenacaoMvcTest {

    private static final String EMAIL = "paciente.ordenacao.mvc@unidadesaude.com.br";
    private static final String SENHA = "Teste-Ord-123";
    private static final String LOGIN = "{\"email\":\"" + EMAIL + "\",\"senha\":\"" + SENHA + "\"}";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private Filter springSecurityFilterChain;
    @Autowired
    private UsuarioController usuarioController;
    @Autowired
    private PerfilRepository perfilRepository;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Long usuarioId;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        Long adminPerfilId = perfilRepository.findByNomeIgnoreCase("Administrador").orElseThrow().getId();
        usuarioId = usuarioController
                .criar(new UsuarioRequest("Paciente Ordenacao MVC", EMAIL, SENHA, 1L, List.of(adminPerfilId))).getId();
    }

    @AfterEach
    void limpar() {
        usuarioController.excluir(usuarioId);
    }

    @Test
    void ordenacaoUnicaDescendenteViaHttp() throws Exception {
        String token = logar();
        MvcResult res = mvc.perform(get("/paciente")
                        .param("situacao", "TODOS").param("size", "100").param("ordenar", "codigo:desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();

        List<Long> ids = ids(res);
        assertTrue(ids.size() >= 2, "esperado ao menos 2 pacientes para conferir a ordem");
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i - 1) > ids.get(i),
                    "ordenar=codigo:desc deve voltar ids decrescentes (regressão do separador vírgula)");
        }
    }

    private String logar() throws Exception {
        MvcResult login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk()).andReturn();
        return login.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private List<Long> ids(MvcResult res) throws Exception {
        JsonNode content = json.readTree(res.getResponse().getContentAsString()).get("content");
        List<Long> ids = new ArrayList<>();
        content.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }
}
