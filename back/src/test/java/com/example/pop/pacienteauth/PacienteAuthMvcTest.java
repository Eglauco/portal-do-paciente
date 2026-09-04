package com.example.pop.pacienteauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import com.example.pop.auth.AuthController;
import com.example.pop.auth.LoginRequest;
import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.perfil.PerfilRepository;
import com.example.pop.usuario.UsuarioController;
import com.example.pop.usuario.UsuarioRequest;
import com.example.pop.verificacao.VerificacaoService;

import jakarta.servlet.Filter;

/** Cadeia de filtros real: /paciente-auth/solicitar-codigo e /ativar públicos, /me e /paciente/** protegidos. */
@SpringBootTest
class PacienteAuthMvcTest {

    private static final String TEL = "11977776666";
    private static final String ADMIN_EMAIL = "paci.mvc.admin@unidadesaude.com.br";
    private static final String ADMIN_SENHA = "Teste-Mvc-123";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private Filter springSecurityFilterChain;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteRepository repository;
    @Autowired
    private UsuarioController usuarioController;
    @Autowired
    private AuthController authController;
    @Autowired
    private PerfilRepository perfilRepository;
    /** Twilio Verify mockado: aprova qualquer código no teste. */
    @MockitoBean
    private VerificacaoService verificacao;

    private MockMvc mvc;
    private Long pacienteId;
    private Long adminId;
    private String adminToken;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        repository.findByTelefone(TEL).ifPresent(p -> repository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente MVC", TEL)).getId();
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        // Admin de teste (para as chamadas /paciente/** que agora exigem role ADMIN).
        Long adminPerfilId = perfilRepository.findByNomeIgnoreCase("Administrador").orElseThrow().getId();
        adminId = usuarioController
                .criar(new UsuarioRequest("Admin Paci MVC", ADMIN_EMAIL, ADMIN_SENHA, 1L, List.of(adminPerfilId)))
                .getId();
        adminToken = authController.login(new LoginRequest(ADMIN_EMAIL, ADMIN_SENHA)).token();
    }

    @AfterEach
    void limpar() {
        repository.deleteById(pacienteId);
        usuarioController.excluir(adminId);
    }

    @Test
    void ativarPublicoEMeProtegido() throws Exception {
        String corpo = "{\"telefone\":\"" + TEL + "\",\"codigo\":\"000000\",\"dispositivoId\":\"dev-mvc\"}";
        MvcResult res = mvc.perform(post("/paciente-auth/ativar")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk()).andReturn();
        String token = res.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertFalse(token.isBlank());

        // Sem token → 401
        mvc.perform(get("/paciente-auth/me")).andExpect(status().isUnauthorized());

        // Com token → 200 e traz o nome
        MvcResult me = mvc.perform(get("/paciente-auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertTrue(me.getResponse().getContentAsString().contains("Paciente MVC"));
    }

    /**
     * Regressão: salvar paciente com "ativo": null no corpo não pode quebrar a
     * desserialização (o DTO ignora ativo). Reproduz o erro relatado pelo admin.
     */
    @Test
    void salvarComAtivoNuloNaoQuebra() throws Exception {
        String tel = "11966665555";
        repository.findByTelefone(tel).ifPresent(p -> repository.deleteById(p.getId()));
        try {
            String corpo = "{\"nome\":\"Teste Ativo Nulo\",\"telefone\":\"" + tel + "\",\"ativo\":null}";
            mvc.perform(post("/paciente").header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isCreated());
        } finally {
            repository.findByTelefone(tel).ifPresent(p -> repository.deleteById(p.getId()));
        }
    }

    /**
     * O paciente pede o próprio código (self-service): endpoint PÚBLICO, sem admin.
     * Telefone cadastrado → 204; telefone desconhecido → 404 com orientação.
     */
    @Test
    void solicitarCodigoEhPublico() throws Exception {
        String corpo = "{\"telefone\":\"" + TEL + "\"}";
        mvc.perform(post("/paciente-auth/solicitar-codigo")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNoContent());

        String desconhecido = "{\"telefone\":\"11900000000\"}";
        mvc.perform(post("/paciente-auth/solicitar-codigo")
                        .contentType(MediaType.APPLICATION_JSON).content(desconhecido))
                .andExpect(status().isNotFound());
    }
}
