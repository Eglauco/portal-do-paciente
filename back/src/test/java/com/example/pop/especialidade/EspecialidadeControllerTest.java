package com.example.pop.especialidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class EspecialidadeControllerTest {

    @Autowired
    private EspecialidadeController controller;

    @Test
    void listaEspecialidadesSemeadas() {
        Pagina<Especialidade> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 8, "esperado ao menos as especialidades semeadas");
        assertTrue(pagina.content().size() <= 10);
        assertTrue(pagina.first());
    }

    @Test
    void filtraPorNome() {
        Pagina<Especialidade> pagina = controller.listar(null, "Cardio", 0, 10);
        assertEquals(1, pagina.totalElements());
        assertEquals("Cardiologia", pagina.content().get(0).getNome());
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<Especialidade> pagina = controller.listar(null, null, 0, 500);
        assertEquals(100, pagina.size());
    }
}
