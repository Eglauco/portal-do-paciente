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
        Pagina<Paciente> pagina = controller.listar(null, null, null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 10, "esperado ao menos os pacientes semeados");
        assertTrue(pagina.content().size() <= 10);
        assertTrue(pagina.first());
    }

    @Test
    void filtraPorNome() {
        Pagina<Paciente> pagina = controller.listar(null, "Ramalho", null, null, 0, 10);
        assertEquals(1, pagina.totalElements());
        assertEquals("Beatriz Ramalho", pagina.content().get(0).getNome());
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<Paciente> pagina = controller.listar(null, null, null, null, 0, 500);
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
                List.of("(11) 90000-0002", "11900000002", "   ")));
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
            assertEquals(1, controller.listar(null, null, "529.982.247-25", null, 0, 10).totalElements());
            assertEquals(1, controller.listar(null, null, null, "PRONT-A", 0, 10).totalElements());
        } finally {
            controller.excluir(p.getId());
        }
    }

    @Test
    void cpfInvalidoRejeita() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.criar(minimo("CPF Ruim", "529.982.247-24", null)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void cnsInvalidoRejeita() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.criar(minimo("CNS Ruim", null, "700000000000001")));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void cpfDuplicadoRejeita() {
        limparResiduos(null, "52998224725");
        Paciente a = controller.criar(minimo("Primeiro CPF", CPF_A, null));
        try {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.criar(minimo("Segundo CPF", CPF_A, null)));
            assertEquals(409, ex.getStatusCode().value());
        } finally {
            controller.excluir(a.getId());
        }
    }

    /** Request com nome + (opcional) cpf/cns e nada mais. */
    private static PacienteRequest minimo(String nome, String cpf, String cns) {
        return new PacienteRequest(nome, null, null, null, null, null, null, cpf, null, null, null, null, null, null,
                null, null, null, null, cns, null);
    }

    private void limparResiduos(String telefone, String cpf) {
        if (telefone != null) {
            repository.findByTelefone(telefone).ifPresent(x -> controller.excluir(x.getId()));
        }
        if (cpf != null) {
            controller.listar(null, null, cpf, null, 0, 100).content()
                    .forEach(x -> controller.excluir(x.getId()));
        }
    }
}
