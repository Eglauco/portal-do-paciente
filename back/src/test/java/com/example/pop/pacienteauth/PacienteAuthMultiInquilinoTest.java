package com.example.pop.pacienteauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.example.pop.inquilino.PacienteIdentidade;
import com.example.pop.inquilino.PacienteIdentidadeRepository;
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
    private static final String SCHEMA2 = "inq_teste_pacauth2";
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
    private PacienteIdentidadeRepository pacienteIdentidadeRepository;
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
        // 2) Credencial CENTRAL no public (Fase B): o PIN vive no public.paciente_identidade, NÃO no
        // conta_app do inquilino. A sessão (conta_app) é criada pelo próprio loginPorSenha (upsertConta).
        PacienteIdentidade identidade = new PacienteIdentidade();
        identidade.setCpf(CPF);
        identidade.setSenhaHash(passwordEncoder.encode(PIN));
        identidade.setSenhaTentativas(0);
        pacienteIdentidadeRepository.save(identidade);
        // 3) Paciente próprio DENTRO do schema do inquilino.
        String anterior = TenantContext.atualBruto();
        TenantContext.definir(SCHEMA);
        try {
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
        // Sem X-Inquilino e sem inquilinoId: caminho do app compartilhado — resolve as clínicas do CPF.
        // Como o CPF só tem 1 clínica (o tenant do teste), entra direto e a sessão vem preenchida.
        var resposta = pacienteAuthController.loginSenha(new LoginSenhaRequest(CPF, PIN, DISPOSITIVO, null));

        assertEquals("Paciente PacAuth", resposta.sessao().nome(),
                "achou o paciente no schema do inquilino (não no public)");
        assertEquals(tenantId.longValue(), inqDoToken(resposta.sessao().token()),
                "o token carrega o inquilino resolvido pelo CPF (não o padrão)");
    }

    /**
     * App COMPARTILHADO: CPF em DUAS clínicas (sem X-Inquilino, sem inquilinoId) → devolve a lista pra
     * escolher (sem sessão). Escolhendo a clínica que tem o paciente, entra (sessão + inq no token).
     */
    @Test
    void appCompartilhadoComCpfEmDuasClinicasDevolveListaParaEscolher() {
        // 2ª clínica (só o registro + o ponteiro; LISTAR não acessa o schema de dados dela).
        Inquilino outra = new Inquilino();
        outra.setNome("Clinica Dois");
        outra.setSchemaName(SCHEMA2);
        Long outraId = inquilinoRepository.save(outra).getId();
        roteamentoPacienteService.registrar(CPF, outraId);

        var lista = pacienteAuthController.loginSenha(new LoginSenhaRequest(CPF, PIN, DISPOSITIVO, null));
        assertNull(lista.sessao(), "com 2 clínicas não entra direto — primeiro escolhe");
        assertNotNull(lista.escolherClinica(), "devolve a lista de clínicas");
        assertEquals(2, lista.escolherClinica().size(), "as 2 clínicas vinculadas ao CPF");

        // Escolhe a clínica que TEM o paciente (tenantId) → entra; a sessão vem preenchida com o inq certo.
        var escolhida = pacienteAuthController.loginSenha(new LoginSenhaRequest(CPF, PIN, DISPOSITIVO, tenantId));
        assertNotNull(escolhida.sessao(), "após escolher a clínica certa, entra");
        assertEquals(tenantId.longValue(), inqDoToken(escolhida.sessao().token()),
                "o token carrega o inquilino escolhido");
    }

    /** Lê o claim {@code inq} do payload do JWT (sem verificar assinatura — basta inspecionar o conteúdo). */
    private static long inqDoToken(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"inq\":(\\d+)").matcher(payload);
        assertTrue(m.find(), "token tem o claim inq");
        return Long.parseLong(m.group(1));
    }

    private void limpar() {
        pacienteLoginRepository.deleteAll(pacienteLoginRepository.findByCpf(CPF));
        pacienteIdentidadeRepository.findByCpf(CPF).ifPresent(pacienteIdentidadeRepository::delete);
        inquilinoRepository.findBySchemaName(SCHEMA).ifPresent(inquilinoRepository::delete);
        // 2ª clínica do teste multi-clínica (só o registro; não tem schema provisionado).
        inquilinoRepository.findBySchemaName(SCHEMA2).ifPresent(inquilinoRepository::delete);
        provisionamentoService.dropSchema(SCHEMA);
    }
}
