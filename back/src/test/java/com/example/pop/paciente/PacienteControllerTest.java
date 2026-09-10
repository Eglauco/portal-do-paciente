package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;

@SpringBootTest
class PacienteControllerTest {

    @Autowired
    private PacienteController controller;
    @Autowired
    private PacienteRepository repository;

    /** CPFs válidos (dígito verificador correto) usados nos testes. */
    private static final String CPF_A = "529.982.247-25";
    private static final String CNS_OK = "798345678901233";

    @Test
    void listaPacientesSemeados() {
        Pagina<Paciente> pagina = controller.listar(null, null, null, null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 10, "esperado ao menos os pacientes semeados");
        assertTrue(pagina.content().size() <= 10);
        assertTrue(pagina.first());
    }

    @Test
    void filtraPorNome() {
        Pagina<Paciente> pagina = controller.listar(null, "Ramalho", null, null, null, 0, 10);
        assertEquals(1, pagina.totalElements());
        assertEquals("Beatriz Ramalho", pagina.content().get(0).getNome());
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<Paciente> pagina = controller.listar(null, null, null, null, null, 0, 500);
        assertEquals(100, pagina.size());
    }

    @Test
    void criaCompletoNormalizaEValida() {
        limparResiduos("11988880001", "52998224725");
        Paciente p = controller.criar(new PacienteRequest(
                "Paciente Completo", "(11) 98888-0001", "COD-INT-A", "PRONT-A",
                Sexo.FEMININO, LocalDate.of(1990, 5, 20), "12.345.678-9", CPF_A,
                "Mãe Teste", "Pai Teste", "Rua A", "100", "Centro", "São Paulo", "sp",
                "01001-000", "Apto 1", "Fulano@Email.com", CNS_OK,
                List.of("(11) 90000-0002", "11900000002", "   "), List.of(), List.of()), null);
        try {
            assertEquals("11988880001", p.getTelefone());
            assertEquals("52998224725", p.getCpf(), "CPF normalizado (só dígitos)");
            assertEquals("SP", p.getUf(), "UF em maiúsculas");
            assertEquals("fulano@email.com", p.getEmail(), "e-mail em minúsculas");
            assertEquals("01001000", p.getCep());
            assertEquals(CNS_OK, p.getCns());
            assertEquals(Sexo.FEMININO, p.getSexo());
            assertEquals(LocalDate.of(1990, 5, 20), p.getDataNascimento());
            // Telefones adicionais: só dígitos, sem repetido nem vazio.
            assertEquals(List.of("11900000002"), p.getTelefonesAdicionais());
            // Filtra por CPF (com máscara) e por prontuário.
            assertEquals(1, controller.listar(null, null, "529.982.247-25", null, null, 0, 10).totalElements());
            assertEquals(1, controller.listar(null, null, null, "PRONT-A", null, 0, 10).totalElements());
        } finally {
            controller.excluir(p.getId());
        }
    }

    @Test
    void responsaveisCriaAtualizaERemove() {
        limparResiduos("11955550001", null);
        // Cria com 2 responsáveis válidos + 1 linha em branco (deve ser ignorada).
        Paciente criado = controller.criar(new PacienteRequest(
                "Paciente Resp", "11955550001", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                List.of(
                        new PacienteRequest.ResponsavelRequest(null, "Maria Mãe", "(11) 98888-1111"),
                        new PacienteRequest.ResponsavelRequest(null, "   ", "irrelevante"),
                        new PacienteRequest.ResponsavelRequest(null, "João Pai", null)), List.of()), null);
        try {
            assertEquals(2, criado.getResponsaveis().size(), "linha em branco é ignorada");
            Responsavel maria = acharPorNome(criado, "Maria Mãe");
            assertEquals("11988881111", maria.getTelefone(), "telefone normalizado (só dígitos)");
            assertEquals(null, acharPorNome(criado, "João Pai").getTelefone(), "telefone opcional");

            // Atualiza: renomeia Maria (mesmo id), remove João, adiciona Ana.
            Paciente atualizado = controller.atualizar(criado.getId(), new PacienteRequest(
                    "Paciente Resp", "11955550001", null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null,
                    List.of(
                            new PacienteRequest.ResponsavelRequest(maria.getId(), "Maria Silva", "11988881111"),
                            new PacienteRequest.ResponsavelRequest(null, "Ana Avó", "11977772222")), List.of()), null).getBody();
            List<String> nomes = atualizado.getResponsaveis().stream().map(Responsavel::getNome).sorted().toList();
            assertEquals(List.of("Ana Avó", "Maria Silva"), nomes, "João removido, Maria renomeada, Ana criada");
            // O id da Maria foi preservado (atualização, não recriação).
            assertEquals(maria.getId(), acharPorNome(atualizado, "Maria Silva").getId());
        } finally {
            controller.excluir(criado.getId());
        }
    }

    private static Responsavel acharPorNome(Paciente p, String nome) {
        return p.getResponsaveis().stream().filter(r -> r.getNome().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void naoPermiteResponsaveisComTelefoneDuplicado() {
        limparResiduos("11955550002", null);
        // Dois responsáveis com o mesmo telefone (um mascarado) → 409.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.criar(new PacienteRequest(
                        "Paciente Dup", "11955550002", null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        List.of(
                                new PacienteRequest.ResponsavelRequest(null, "Ana", "11988887777"),
                                new PacienteRequest.ResponsavelRequest(null, "Bia", "(11) 98888-7777")),
                        List.of()), null));
        assertEquals(409, ex.getStatusCode().value(), "dois responsáveis com o mesmo telefone");
    }

    @Test
    void naoPermiteResponsavelComTelefoneDoPaciente() {
        limparResiduos("11955550003", null);
        // Responsável com o telefone do próprio paciente → 422 (tem de ser outra pessoa).
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.criar(new PacienteRequest(
                        "Paciente Igual", "11955550003", null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        List.of(new PacienteRequest.ResponsavelRequest(null, "Clone", "11955550003")),
                        List.of()), null));
        assertEquals(422, ex.getStatusCode().value(), "responsável com o telefone do próprio paciente");
    }

    @Test
    void cpfInvalidoRejeita() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.criar(minimo("CPF Ruim", "529.982.247-24", null), null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void cnsInvalidoRejeita() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.criar(minimo("CNS Ruim", null, "700000000000001"), null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void cpfDuplicadoRejeita() {
        limparResiduos(null, "52998224725");
        Paciente a = controller.criar(minimo("Primeiro CPF", CPF_A, null), null);
        try {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.criar(minimo("Segundo CPF", CPF_A, null), null));
            assertEquals(409, ex.getStatusCode().value());
        } finally {
            controller.excluir(a.getId());
        }
    }

    /** Request com nome + (opcional) cpf/cns e nada mais. */
    private static PacienteRequest minimo(String nome, String cpf, String cns) {
        return new PacienteRequest(nome, null, null, null, null, null, null, cpf, null, null, null, null, null, null,
                null, null, null, null, cns, null, null, null);
    }

    private void limparResiduos(String telefone, String cpf) {
        if (telefone != null) {
            repository.findByTelefone(telefone).ifPresent(x -> controller.excluir(x.getId()));
        }
        if (cpf != null) {
            controller.listar(null, null, cpf, null, "TODOS", 0, 100).content()
                    .forEach(x -> controller.excluir(x.getId()));
        }
    }
}
