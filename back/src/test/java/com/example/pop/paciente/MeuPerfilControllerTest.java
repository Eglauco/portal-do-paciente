package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

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
    private static final String CPF = "10000000020";
    private static final java.time.LocalDate DOB = java.time.LocalDate.of(1990, 1, 1);
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
    private PacienteLogService logService;
    @Autowired
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private VerificacaoService verificacao;

    private Long pacienteId;
    private Jwt jwt;

    @BeforeEach
    void setup() {
        repository.buscarPorTelefoneNaLista(TEL).forEach(p -> repository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Paciente Perfil", TEL), null).getId();
        repository.findById(pacienteId).ifPresent(p -> {
            p.setCpf(CPF);
            p.setDataNascimento(DOB);
            repository.save(p);
        });
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        jwt = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(CPF, DOB, "000000", "dev-perfil", TEL)).token());
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
        assertTrue(perfil.telefonesAdicionais().contains(TEL));
        assertEquals("mariana@example.com", perfil.email());
        assertTrue(perfil.telefonesAdicionais().contains("11955550000"));
        assertNull(perfil.fotoUrl());
    }

    @Test
    void atualizarAlteraDadosPessoaisEAuditaComoPaciente() {
        MeuPerfilRequest req = new MeuPerfilRequest("Paciente Editado", List.of(TEL, "11955551234"),
                Sexo.FEMININO, java.time.LocalDate.of(1991, 2, 3), "12.345.678-9", null,
                "Mãe Editada", "Pai Editado", "Novo@Example.com",
                "Rua Nova", "200", "Casa", "Centro", "São Paulo", "sp", "01001-000");

        MeuPerfilResponse perfil = controller.atualizar(jwt, req);
        assertEquals("Paciente Editado", perfil.nome());
        assertEquals("novo@example.com", perfil.email(), "e-mail normalizado (minúsculas)");
        assertEquals("SP", perfil.uf(), "UF em maiúsculas");
        assertTrue(perfil.telefonesAdicionais().contains("11955551234"));
        assertEquals(CPF, perfil.cpf(), "CPF não muda pelo app");

        Paciente p = repository.findById(pacienteId).orElseThrow();
        assertEquals("01001000", p.getCep(), "CEP só dígitos");
        assertEquals(CPF, p.getCpf());

        boolean auditado = logService.listar(pacienteId).stream()
                .anyMatch(l -> l.autor() == AutorLogPaciente.PACIENTE && l.tipo() == TipoEventoPaciente.ALTERACAO);
        assertTrue(auditado, "editar o próprio perfil deve gerar log de auditoria com autor PACIENTE");
    }

    @Test
    void atualizarSemNenhumTelefoneRetorna422() {
        MeuPerfilRequest req = new MeuPerfilRequest("Paciente Perfil", List.of(),
                null, DOB, null, null, null, null, null, null, null, null, null, null, null, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.atualizar(jwt, req));
        assertEquals(422, ex.getStatusCode().value());
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
