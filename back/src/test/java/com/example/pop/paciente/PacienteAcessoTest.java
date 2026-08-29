package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.pacienteauth.PacienteSessaoResponse;

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

    private Long pacienteId;

    @BeforeEach
    void criar() {
        repository.findByTelefone(TEL).ifPresent(p -> repository.deleteById(p.getId()));
        Paciente p = new Paciente();
        p.setNome("Paciente Teste");
        p.setTelefone(TEL);
        pacienteId = pacienteController.criar(p).getId();
    }

    @AfterEach
    void limpar() {
        repository.deleteById(pacienteId);
    }

    private String gerarCodigo() {
        return pacienteController.gerarCodigo(pacienteId).getBody().codigo();
    }

    @Test
    void fluxoAtivarEmiteTokenAmarradoAoAparelho() {
        String codigo = gerarCodigo();
        assertEquals(6, codigo.length());

        PacienteSessaoResponse sessao = authController.ativar(new AtivarPacienteRequest(TEL, codigo, "dev-A"));
        assertNotNull(sessao.token());
        assertEquals(pacienteId, sessao.pacienteId());

        Jwt jwt = jwtDecoder.decode(sessao.token());
        assertEquals("PACIENTE", jwt.getClaimAsString("role"));
        assertEquals("dev-A", jwt.getClaimAsString("dev"));
        assertEquals(pacienteId, ((Number) jwt.getClaim("pid")).longValue());

        assertDoesNotThrow(() -> acessoService.validarSessao(pacienteId, "dev-A"));

        // Código é de uso único → reusar falha.
        assertThrows(ResponseStatusException.class,
                () -> authController.ativar(new AtivarPacienteRequest(TEL, codigo, "dev-A")));
    }

    @Test
    void trocarDeAparelhoInvalidaOAnterior() {
        authController.ativar(new AtivarPacienteRequest(TEL, gerarCodigo(), "dev-A"));
        authController.ativar(new AtivarPacienteRequest(TEL, gerarCodigo(), "dev-B"));

        assertThrows(ResponseStatusException.class, () -> acessoService.validarSessao(pacienteId, "dev-A"));
        assertDoesNotThrow(() -> acessoService.validarSessao(pacienteId, "dev-B"));
    }

    @Test
    void codigoErradoRetorna401() {
        gerarCodigo();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.ativar(new AtivarPacienteRequest(TEL, "codigo-errado", "dev-A")));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void semLiberacaoNaoAtiva() {
        // Sem gerar código, o paciente não está ativo → 401.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authController.ativar(new AtivarPacienteRequest(TEL, "123456", "dev-A")));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void revogarInvalidaSessao() {
        authController.ativar(new AtivarPacienteRequest(TEL, gerarCodigo(), "dev-A"));
        acessoService.revogar(repository.findById(pacienteId).orElseThrow());
        assertThrows(ResponseStatusException.class, () -> acessoService.validarSessao(pacienteId, "dev-A"));
    }
}
