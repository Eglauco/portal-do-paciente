package com.example.pop.pacienteauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.pop.inquilino.Inquilino;
import com.example.pop.inquilino.InquilinoRepository;
import com.example.pop.inquilino.InquilinoService;
import com.example.pop.inquilino.PacienteLoginRepository;
import com.example.pop.inquilino.ProvisionamentoService;
import com.example.pop.inquilino.RoteamentoPacienteService;
import com.example.pop.paciente.ContaApp;
import com.example.pop.paciente.ContaAppRepository;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.SituacaoCadastro;
import com.example.pop.tenant.TenantContext;

/**
 * Login do paciente em 2 fases (Fase 1.3b): o app NÃO informa o inquilino — ele é resolvido pelo CPF
 * em {@code public.paciente_login}. Este teste prova a rota: uma conta/paciente que existe SOMENTE no
 * schema de um inquilino provisionado (não no {@code public}) consegue logar por senha, e o token
 * sai com o claim {@code inq} apontando para esse inquilino — se o roteamento falhasse (caísse no
 * public), {@code loginPorSenha} não acharia a conta e devolveria 401.
 */
@SpringBootTest
class PacienteAuthMultiInquilinoTest {

    private static final String SCHEMA = "inq_teste_pacauth";
    private static final String CPF = "52998224725";
    private static final String PIN = "123456";
    private static final String DISPOSITIVO = "disp-teste-pacauth";

    @Autowired
    private PacienteAuthController pacienteAuthController;
    @Autowired
    private ProvisionamentoService provisionamentoService;
    @Autowired
    private InquilinoRepository inquilinoRepository;
    @Autowired
    private InquilinoService inquilinoService;
    @Autowired
    private PacienteLoginRepository pacienteLoginRepository;
    @Autowired
    private RoteamentoPacienteService roteamentoPacienteService;
    @Autowired
    private ContaAppRepository contaAppRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long tenantId;

    @BeforeEach
    void setup() {
        limpar();
        // 1) Registro + schema do inquilino (baseline limpo: sem pacientes de demo).
        Inquilino inquilino = new Inquilino();
        inquilino.setNome("Clinica PacAuth");
        inquilino.setSchemaName(SCHEMA);
        tenantId = inquilinoRepository.save(inquilino).getId();
        provisionamentoService.provisionarInquilino(SCHEMA);
        // 2) Conta (com senha) + paciente próprio DENTRO do schema do inquilino.
        String anterior = TenantContext.atualBruto();
        TenantContext.definir(SCHEMA);
        try {
            LocalDateTime agora = LocalDateTime.now();
            ContaApp conta = new ContaApp();
            conta.setCpf(CPF);
            conta.setSenhaHash(passwordEncoder.encode(PIN));
            conta.setSenhaTentativas(0);
            conta.setCriadoEm(agora);
            conta.setAtualizadoEm(agora);
            contaAppRepository.save(conta);

            Paciente paciente = new Paciente();
            paciente.setNome("Paciente PacAuth");
            paciente.setCpf(CPF);
            paciente.setDataNascimento(LocalDate.of(1990, 1, 1));
            paciente.setSituacao(SituacaoCadastro.ATIVO);
            pacienteRepository.save(paciente);
        } finally {
            if (anterior != null) {
                TenantContext.definir(anterior);
            } else {
                TenantContext.limpar();
            }
        }
        // 3) Ponteiro de roteamento cpf → inquilino (o que o app usa para resolver o schema).
        roteamentoPacienteService.registrar(CPF, tenantId);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void loginPorSenhaRoteiaPeloCpfECarimbaOInquilinoNoToken() {
        var resposta = pacienteAuthController.loginSenha(new LoginSenhaRequest(CPF, PIN, DISPOSITIVO));

        assertEquals("Paciente PacAuth", resposta.nome(), "achou o paciente no schema do inquilino (não no public)");
        assertEquals(tenantId.longValue(), inqDoToken(resposta.token()),
                "o token carrega o inquilino resolvido pelo CPF (não o padrão)");
    }

    /** Lê o claim {@code inq} do payload do JWT (sem verificar assinatura — basta inspecionar o conteúdo). */
    private static long inqDoToken(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"inq\":(\\d+)").matcher(payload);
        assertTrue(m.find(), "token tem o claim inq");
        return Long.parseLong(m.group(1));
    }

    private void limpar() {
        pacienteLoginRepository.findByCpf(CPF).ifPresent(pacienteLoginRepository::delete);
        inquilinoRepository.findBySchemaName(SCHEMA).ifPresent(inquilinoRepository::delete);
        provisionamentoService.dropSchema(SCHEMA);
    }
}
