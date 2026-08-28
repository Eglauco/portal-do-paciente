package com.example.pop.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;

@SpringBootTest
class UsuarioControllerTest {

    @Autowired
    private UsuarioController controller;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    void criaComSenhaEValidaRegras() {
        Usuario novo = controller.criar(
                new UsuarioRequest("Teste Senha", "teste.senha@unidadesaude.com.br", "segredo123", 1L));
        Long id = novo.getId();
        assertNotNull(id);
        assertTrue(passwordEncoder.matches("segredo123", novo.getSenhaHash()), "a senha deve ser guardada com hash");
        assertNotNull(novo.getUnidade(), "o usuário deve ficar vinculado à unidade");
        assertEquals(1L, novo.getUnidade().getId());

        // E-mail duplicado → 409
        ResponseStatusException dup = assertThrows(ResponseStatusException.class, () -> controller
                .criar(new UsuarioRequest("Outro", "teste.senha@unidadesaude.com.br", "segredo123", 1L)));
        assertEquals(409, dup.getStatusCode().value());

        // Senha curta → 400
        ResponseStatusException curta = assertThrows(ResponseStatusException.class,
                () -> controller.criar(new UsuarioRequest("Curta", "curta@unidadesaude.com.br", "123", 1L)));
        assertEquals(400, curta.getStatusCode().value());

        // Editar sem senha mantém o hash; com senha, troca.
        String hashAntigo = novo.getSenhaHash();
        controller.atualizar(id, new UsuarioRequest("Teste Senha 2", "teste.senha@unidadesaude.com.br", "", 1L));
        assertEquals(hashAntigo, controller.buscar(id).getBody().getSenhaHash());

        controller.atualizar(id,
                new UsuarioRequest("Teste Senha 2", "teste.senha@unidadesaude.com.br", "novaSenha123", 1L));
        assertTrue(passwordEncoder.matches("novaSenha123", controller.buscar(id).getBody().getSenhaHash()));

        controller.excluir(id);
    }

    @Test
    void filtraPorEmailUnico() {
        Pagina<Usuario> pagina = controller.listar(null, null, "rafael.lima@unidadesaude.com.br", 0, 50);
        assertEquals(1, pagina.totalElements());
        assertEquals("Rafael Lima", pagina.content().get(0).getNome());
    }
}
