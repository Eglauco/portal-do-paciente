package com.example.pop.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class UsuarioControllerTest {

    @Autowired
    private UsuarioController controller;

    @Test
    void paginaPadraoTraz10Registros() {
        Pagina<Usuario> pagina = controller.listar(null, null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 120, "esperado ao menos os registros semeados");
        assertEquals(10, pagina.content().size());
        assertEquals(10, pagina.size());
        assertTrue(pagina.first());
        assertEquals("Administrador", pagina.content().get(0).getNome());
        assertEquals((int) Math.ceil(pagina.totalElements() / 10.0), pagina.totalPages());
    }

    @Test
    void aceitaTamanho100() {
        Pagina<Usuario> pagina = controller.listar(null, null, null, 0, 100);
        assertEquals(100, pagina.size());
        assertEquals(100, pagina.content().size());
    }

    @Test
    void tamanhoAcimaDoLimiteEhReduzidoPara100() {
        Pagina<Usuario> pagina = controller.listar(null, null, null, 0, 500);
        assertEquals(100, pagina.size());
        assertEquals(100, pagina.content().size());
    }

    @Test
    void navegaParaSegundaPagina() {
        Pagina<Usuario> pagina = controller.listar(null, null, null, 1, 50);
        assertEquals(1, pagina.page());
        assertFalse(pagina.first());
    }

    @Test
    void filtraPorEmailUnico() {
        Pagina<Usuario> pagina = controller.listar(null, null, "rafael.lima@unidadesaude.com.br", 0, 50);
        assertEquals(1, pagina.totalElements());
        assertEquals("Rafael Lima", pagina.content().get(0).getNome());
    }
}
