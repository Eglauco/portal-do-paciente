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
    private static final String CPF = "10000000010";
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
        repository.buscarPorTelefoneNaLista(TEL).forEach(p -> repository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente MVC", TEL), null).getId();
        repository.findById(pacienteId).ifPresent(p -> {
            p.setCpf(CPF);
            p.setDataNascimento(java.time.LocalDate.of(1990, 1, 1));
            repository.save(p);
        });
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        // Admin de teste (para as chamadas /paciente/** que agora exigem role ADMIN).
        Long adminPerfilId = perfilRepository.findByNomeIgnoreCase("Administrador").orElseThrow().getId();
        adminId = usuarioController
                .criar(new UsuarioRequest("Admin Paci MVC", ADMIN_EMAIL, ADMIN_SENHA, 1L, List.of(adminPerfilId), null))
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
        String corpo = "{\"cpf\":\"" + CPF + "\",\"dataNascimento\":\"1990-01-01\","
                + "\"codigo\":\"000000\",\"dispositivoId\":\"dev-mvc\",\"telefone\":\"" + TEL + "\"}";
        MvcResult res = mvc.perform(post("/paciente-auth/ativar")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk()).andReturn();
        String token = res.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertFalse(token.isBlank());
        // Fase 0.2 (multi-inquilino): o token carrega o inquilino no claim "inq".
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"inq\""), "token do paciente deve carregar o claim do inquilino");

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
        repository.buscarPorTelefoneNaLista(tel).forEach(p -> repository.deleteById(p.getId()));
        try {
            String corpo = "{\"nome\":\"Teste Ativo Nulo\",\"telefonesAdicionais\":[\"" + tel + "\"],\"ativo\":null}";
            mvc.perform(post("/paciente").header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isCreated());
        } finally {
            repository.buscarPorTelefoneNaLista(tel).forEach(p -> repository.deleteById(p.getId()));
        }
    }

    /**
     * O paciente pede o próprio código (self-service): endpoint PÚBLICO, sem admin.
     * CPF+data conferem → 200 (com telefone mascarado); não conferem → 401 genérico
     * (não revela se o CPF existe nem qual campo falhou).
     */
    @Test
    void solicitarCodigoEhPublico() throws Exception {
        String corpo = "{\"cpf\":\"" + CPF + "\",\"dataNascimento\":\"1990-01-01\",\"telefone\":\"" + TEL + "\"}";
        mvc.perform(post("/paciente-auth/solicitar-codigo")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        String desconhecido = "{\"cpf\":\"19999999999\",\"dataNascimento\":\"1990-01-01\",\"telefone\":\"" + TEL + "\"}";
        mvc.perform(post("/paciente-auth/solicitar-codigo")
                        .contentType(MediaType.APPLICATION_JSON).content(desconhecido))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Login por SENHA (botão "Já tenho senha"): depois de ativar e DEFINIR o PIN, entra só com
     * CPF + senha — sem telefone nem data de nascimento. Senha errada → 401. (O OTP do passo 1
     * zera a senha, então o definir-senha funciona em toda rodada mesmo reusando a conta.)
     */
    @Test
    void loginPorSenhaSoComCpfESenha() throws Exception {
        // 1) Ativa (OTP) para obter um token e poder definir a senha.
        String ativarBody = "{\"cpf\":\"" + CPF + "\",\"dataNascimento\":\"1990-01-01\","
                + "\"codigo\":\"000000\",\"dispositivoId\":\"dev-senha\",\"telefone\":\"" + TEL + "\"}";
        MvcResult ativou = mvc.perform(post("/paciente-auth/ativar")
                        .contentType(MediaType.APPLICATION_JSON).content(ativarBody))
                .andExpect(status().isOk()).andReturn();
        String token = ativou.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        // 2) Define o PIN de 6 dígitos (autenticado com o token do passo 1).
        mvc.perform(post("/paciente-auth/definir-senha").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"123456\"}"))
                .andExpect(status().isOk());

        // 3) Login só com CPF + senha (sem telefone/data) → 200 com token.
        String loginBody = "{\"cpf\":\"" + CPF + "\",\"senha\":\"123456\",\"dispositivoId\":\"dev-senha\"}";
        MvcResult logou = mvc.perform(post("/paciente-auth/login-senha")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn();
        assertFalse(logou.getResponse().getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1").isBlank());

        // 4) Senha errada → 401 (sem revelar a identidade).
        String errado = "{\"cpf\":\"" + CPF + "\",\"senha\":\"000000\",\"dispositivoId\":\"dev-senha\"}";
        mvc.perform(post("/paciente-auth/login-senha")
                        .contentType(MediaType.APPLICATION_JSON).content(errado))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Regressão de segurança: a trava de 5 tentativas do login por senha PRECISA acumular entre
     * requisições. Como o ramo de PIN errado incrementa o contador e logo lança 401, o
     * @Transactional tem de usar noRollbackFor (senão o rollback zera o contador e a força-bruta
     * seria ilimitada — defesa única do caminho CPF+PIN). Após 5 erros, até a senha CERTA → 429.
     */
    @Test
    void loginPorSenhaBloqueiaAposCincoTentativas() throws Exception {
        String ativarBody = "{\"cpf\":\"" + CPF + "\",\"dataNascimento\":\"1990-01-01\","
                + "\"codigo\":\"000000\",\"dispositivoId\":\"dev-lock\",\"telefone\":\"" + TEL + "\"}";
        MvcResult ativou = mvc.perform(post("/paciente-auth/ativar")
                        .contentType(MediaType.APPLICATION_JSON).content(ativarBody))
                .andExpect(status().isOk()).andReturn();
        String token = ativou.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/paciente-auth/definir-senha").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"123456\"}"))
                .andExpect(status().isOk());

        // 5 tentativas com PIN errado → 401 cada (o contador precisa persistir apesar do throw).
        String errado = "{\"cpf\":\"" + CPF + "\",\"senha\":\"000000\",\"dispositivoId\":\"dev-lock\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/paciente-auth/login-senha")
                            .contentType(MediaType.APPLICATION_JSON).content(errado))
                    .andExpect(status().isUnauthorized());
        }

        // 6ª tentativa, agora com a senha CERTA → bloqueada por tentativas (429), não 200.
        String certo = "{\"cpf\":\"" + CPF + "\",\"senha\":\"123456\",\"dispositivoId\":\"dev-lock\"}";
        mvc.perform(post("/paciente-auth/login-senha")
                        .contentType(MediaType.APPLICATION_JSON).content(certo))
                .andExpect(status().isTooManyRequests());
    }
}
