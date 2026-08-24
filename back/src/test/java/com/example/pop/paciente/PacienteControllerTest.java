package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class PacienteControllerTest {

    @Autowired
    private PacienteController controller;

    @Test
    void listaPacientesSemeados() {
        Pagina<Paciente> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 10, "esperado ao menos os pacientes semeados");
        assertTrue(pagina.content().size() <= 10);
        assertTrue(pagina.first());
    }

    @Test
    void filtraPorNome() {
        Pagina<Paciente> pagina = controller.listar(null, "Ramalho", 0, 10);
        assertEquals(1, pagina.totalElements());
        assertEquals("Beatriz Ramalho", pagina.content().get(0).getNome());
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<Paciente> pagina = controller.listar(null, null, 0, 500);
        assertEquals(100, pagina.size());
    }
}
