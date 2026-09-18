package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.pacienteauth.SolicitarCodigoRequest;
import com.example.pop.verificacao.CanalVerificacao;
import com.example.pop.verificacao.VerificacaoService;

/** Login por Telefone + CPF + data de nascimento; o OTP vai ao telefone digitado e a sessão fica no aparelho. */
@SpringBootTest
class PacienteAcessoTest {

    private static final String TEL = "11988887777";
    private static final String CPF = "10000000001";
    private static final LocalDate DOB = LocalDate.of(1990, 1, 1);
    /** CPF em formato válido, mas nunca cadastrado (caminho de identidade não confere). */
    private static final String CPF_INEXISTENTE = "19999999999";

    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteAcessoService acessoService;
    @Autowired
    private PacienteRepository repository;
    @Autowired
    private JwtDecoder jwtDecoder;

    /** Twilio Verify é mockado: controlamos aprovação do código sem bater no provedor. */
    @MockitoBean
    private VerificacaoService verificacao;

    private Long pacienteId;

    @BeforeEach
    void criar() {
        repository.buscarPorTelefoneNaLista(TEL).forEach(p -> repository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente Teste", TEL), null).getId();
        definirCadastro(pacienteId);
    }

    @AfterEach
    void limpar() {
        repository.deleteById(pacienteId);
    }

    /** Define CPF + data de nascimento direto na entidade (o atalho de criação não os coleta). */
    private void definirCadastro(Long id) {
        Paciente p = repository.findById(id).orElseThrow();
        p.setCpf(CPF);
        p.setDataNascimento(DOB);
        repository.save(p);
    }

    @Test
    void e164AssumeBrasil() {
        assertEquals("+5511988887777", PacienteAcessoService.e164("(11) 98888-7777"));
        assertEquals("+5511988887777", PacienteAcessoService.e164("5511988887777"));
    }

    @Test
    void solicitarCodigoEnviaPorSmsQuandoTelefoneCpfEDataConferem() {
        authController.solicitarCodigo(new SolicitarCodigoRequest(CPF, DOB, TEL));
        verify(verificacao).enviar("+5511988887777", CanalVerificacao.SMS);
    }

    @Test
    void solicitarCodigoRespeitaCooldown() {
        String tel2 = "11944443333";
        String cpf2 = "10000000002";
        repository.buscarPorTelefoneNaLista(tel2).forEach(p -> repository.deleteById(p.getId()));
        Long id2 = pacienteController.criar(new PacienteRequest("Cooldown Teste", tel2), null).getId();
        Paciente p2 = repository.findById(id2).orElseThrow();
        p2.setCpf(cpf2);
        p2.setDataNascimento(DOB);
        repository.save(p2);
        try {
            authController.solicitarCodigo(new SolicitarCodigoRequest(cpf2, DOB, tel2));
            // Segundo pedido imediato para o mesmo CPF → 429 (evita SMS bombing / abuso de custo).
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> authController.solicitarCodigo(new SolicitarCodigoRequest(cpf2, DOB, tel2)));
            assertEquals(429, ex.getStatusCode().value());
        } finally {
            repository.deleteById(id2);
        }
    }

    @Test
    void solicitarCodigoCpfNaoCadastradoRetorna401Generico() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.solicitarCodigo(new SolicitarCodigoRequest(CPF_INEXISTENTE, DOB, TEL)));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void solicitarCodigoDataErradaRetorna401Generico() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.solicitarCodigo(new SolicitarCodigoRequest(CPF, LocalDate.of(1985, 6, 15), TEL)));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void solicitarCodigoTelefoneNaoConfereRetorna401ENaoEnviaSms() {
        // Telefone que NÃO está na lista do paciente: identidade não confere → 401 e nenhum SMS.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.solicitarCodigo(new SolicitarCodigoRequest(CPF, DOB, "11900000000")));
        assertEquals(401, ex.getStatusCode().value());
        verify(verificacao, never()).enviar(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void telefoneCanonicoIgnoraPaisEoNonoDigito() {
        // Match tolerante: mesmo número em formatos variados reduz à mesma forma canônica.
        String base = PacienteAcessoService.telefoneCanonico("11988887777");
        assertEquals(base, PacienteAcessoService.telefoneCanonico("+55 (11) 98888-7777"));
        assertEquals(base, PacienteAcessoService.telefoneCanonico("5511988887777"));
        assertEquals(base, PacienteAcessoService.telefoneCanonico("1188887777")); // sem o 9 do celular
    }

    @Test
    void fluxoAtivarEmiteTokenAmarradoAoAparelho() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);

        var sessao = authController.ativar(new AtivarPacienteRequest(CPF, DOB, "000000", "dev-A", TEL));
        assertNotNull(sessao.token());
        assertEquals(pacienteId, sessao.pacienteId());

        Jwt jwt = jwtDecoder.decode(sessao.token());
        assertEquals("PACIENTE", jwt.getClaimAsString("role"));
        assertEquals("dev-A", jwt.getClaimAsString("dev"));
        assertEquals(pacienteId, ((Number) jwt.getClaim("pid")).longValue());
        assertEquals(CPF, jwt.getSubject());
        assertNotNull(jwt.getClaim("cid"));

        assertDoesNotThrow(() -> acessoService.pacienteDoToken(jwt));
    }

    @Test
    void trocarDeAparelhoInvalidaOAnterior() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        Jwt jwtA = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(CPF, DOB, "000000", "dev-A", TEL)).token());
        Jwt jwtB = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(CPF, DOB, "000000", "dev-B", TEL)).token());

        assertThrows(ResponseStatusException.class, () -> acessoService.pacienteDoToken(jwtA));
        assertDoesNotThrow(() -> acessoService.pacienteDoToken(jwtB));
    }

    @Test
    void codigoErradoRetorna401() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.ativar(new AtivarPacienteRequest(CPF, DOB, "999999", "dev-A", TEL)));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void ativarDataErradaRetorna401Generico() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.ativar(new AtivarPacienteRequest(CPF, LocalDate.of(1985, 6, 15), "000000", "dev-A", TEL)));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void revogarInvalidaSessao() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        var sessao = authController.ativar(new AtivarPacienteRequest(CPF, DOB, "000000", "dev-A", TEL));
        acessoService.revogar(repository.findById(pacienteId).orElseThrow());
        Jwt jwt = jwtDecoder.decode(sessao.token());
        assertThrows(ResponseStatusException.class, () -> acessoService.pacienteDoToken(jwt));
    }
}
