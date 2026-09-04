package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.example.pop.pacienteauth.PacienteSessaoResponse;
import com.example.pop.pacienteauth.SolicitarCodigoRequest;
import com.example.pop.verificacao.CanalVerificacao;
import com.example.pop.verificacao.VerificacaoService;

@SpringBootTest
class PacienteAcessoTest {

    private static final String TEL = "11988887777";

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
        repository.findByTelefone(TEL).ifPresent(p -> repository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente Teste", TEL)).getId();
    }

    @AfterEach
    void limpar() {
        repository.deleteById(pacienteId);
    }

    @Test
    void e164AssumeBrasil() {
        assertEquals("+5511988887777", PacienteAcessoService.e164("(11) 98888-7777"));
        assertEquals("+5511988887777", PacienteAcessoService.e164("5511988887777"));
    }

    @Test
    void solicitarCodigoEnviaPorSms() {
        authController.solicitarCodigo(new SolicitarCodigoRequest(TEL));
        verify(verificacao).enviar("+5511988887777", CanalVerificacao.SMS);
    }

    @Test
    void solicitarCodigoRespeitaCooldown() {
        String tel2 = "11955554444";
        repository.findByTelefone(tel2).ifPresent(p -> repository.deleteById(p.getId()));
        Long id2 = pacienteController.criar(new PacienteRequest("Cooldown Teste", tel2)).getId();
        try {
            authController.solicitarCodigo(new SolicitarCodigoRequest(tel2));
            // Segundo pedido imediato para o mesmo telefone → 429 (evita SMS bombing / abuso de custo).
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> authController.solicitarCodigo(new SolicitarCodigoRequest(tel2)));
            assertEquals(429, ex.getStatusCode().value());
        } finally {
            repository.deleteById(id2);
        }
    }

    @Test
    void solicitarCodigoTelefoneNaoCadastradoRetorna404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.solicitarCodigo(new SolicitarCodigoRequest("11900000000")));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void fluxoAtivarEmiteTokenAmarradoAoAparelho() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);

        PacienteSessaoResponse sessao = authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-A"));
        assertNotNull(sessao.token());
        assertEquals(pacienteId, sessao.pacienteId());

        Jwt jwt = jwtDecoder.decode(sessao.token());
        assertEquals("PACIENTE", jwt.getClaimAsString("role"));
        assertEquals("dev-A", jwt.getClaimAsString("dev"));
        assertEquals(pacienteId, ((Number) jwt.getClaim("pid")).longValue());

        assertDoesNotThrow(() -> acessoService.validarSessao(pacienteId, "dev-A"));
    }

    @Test
    void trocarDeAparelhoInvalidaOAnterior() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-A"));
        authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-B"));

        assertThrows(ResponseStatusException.class, () -> acessoService.validarSessao(pacienteId, "dev-A"));
        assertDoesNotThrow(() -> acessoService.validarSessao(pacienteId, "dev-B"));
    }

    @Test
    void codigoErradoRetorna401() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.ativar(new AtivarPacienteRequest(TEL, "999999", "dev-A")));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void ativarTelefoneNaoCadastradoRetorna401() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.ativar(new AtivarPacienteRequest("11900000000", "000000", "dev-A")));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void revogarInvalidaSessao() {
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-A"));
        acessoService.revogar(repository.findById(pacienteId).orElseThrow());
        assertThrows(ResponseStatusException.class, () -> acessoService.validarSessao(pacienteId, "dev-A"));
    }
}
