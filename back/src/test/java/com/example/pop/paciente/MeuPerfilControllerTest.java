package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.example.pop.verificacao.VerificacaoService;

/** /meu/perfil: leitura dos dados do paciente logado + troca de foto com trava de pasta. */
@SpringBootTest
class MeuPerfilControllerTest {

    private static final String TEL = "11966663333";
    // Sem S3 nos testes, a URL só precisa casar com bucket/pasta (validação de string).
    private static final String BASE = "http://localhost:9000/portal-paciente/";

    @Autowired
    private MeuPerfilController controller;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository repository;
    @Autowired
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private VerificacaoService verificacao;

    private Long pacienteId;
    private Jwt jwt;

    @BeforeEach
    void setup() {
        repository.findByTelefone(TEL).ifPresent(p -> repository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente Perfil", TEL)).getId();
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        jwt = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-perfil")).token());
    }

    @AfterEach
    void limpar() {
        repository.deleteById(pacienteId);
    }

    @Test
    void meuPerfilTrazDadosDoPacienteLogado() {
        Paciente p = repository.findById(pacienteId).orElseThrow();
        p.setEmail("mariana@example.com");
        p.getTelefonesAdicionais().add("11955550000");
        repository.save(p);

        MeuPerfilResponse perfil = controller.meuPerfil(jwt);
        assertEquals("Paciente Perfil", perfil.nome());
        assertEquals(TEL, perfil.telefone());
        assertEquals("mariana@example.com", perfil.email());
        assertTrue(perfil.telefonesAdicionais().contains("11955550000"));
        assertNull(perfil.fotoUrl());
    }

    @Test
    void salvarFotoRejeitaUrlForaDaPastaFotoPaciente() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.salvarFoto(jwt, new MeuPerfilController.SalvarFotoRequest(BASE + "prontuarios/x.jpg")));
        assertEquals(400, ex.getStatusCode().value());
        assertNull(repository.findById(pacienteId).orElseThrow().getFotoUrl());
    }

    @Test
    void salvarFotoAceitaEPersisteUrlDaPasta() {
        String url = BASE + "foto-paciente/abc-foto.jpg";
        MeuPerfilResponse perfil = controller.salvarFoto(jwt, new MeuPerfilController.SalvarFotoRequest(url));
        assertEquals(url, perfil.fotoUrl());
        assertEquals(url, repository.findById(pacienteId).orElseThrow().getFotoUrl());
    }

    @Test
    void removerFotoLimpaAReferencia() {
        controller.salvarFoto(jwt, new MeuPerfilController.SalvarFotoRequest(BASE + "foto-paciente/abc-foto.jpg"));
        MeuPerfilResponse perfil = controller.removerFoto(jwt);
        assertNull(perfil.fotoUrl());
        assertNull(repository.findById(pacienteId).orElseThrow().getFotoUrl());
    }
}
