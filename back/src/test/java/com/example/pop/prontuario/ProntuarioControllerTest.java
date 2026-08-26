package com.example.pop.prontuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;

@SpringBootTest
class ProntuarioControllerTest {

    @Autowired
    private ProntuarioController controller;

    @Autowired
    private ProntuarioRepository repository;

    /** Remove resíduos de execuções anteriores (o banco de dev não é resetado). */
    private void limparResiduosDeTeste() {
        repository.findAll().stream()
                .filter(p -> p.getNumeroAtendimento().startsWith("ATD-TESTE"))
                .forEach(p -> repository.deleteById(p.getId()));
    }

    @Test
    void listaProntuariosSemeados() {
        Pagina<ProntuarioResponse> pagina = controller.listar(null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 3, "esperado ao menos os prontuários semeados");
        assertNotNull(pagina.content().get(0).paciente());
        assertNotNull(pagina.content().get(0).numeroAtendimento());
    }

    @Test
    void filtraPorNumero() {
        Pagina<ProntuarioResponse> pagina = controller.listar("ATD-2026-0001", null, 0, 10);
        assertTrue(pagina.totalElements() >= 1);
        assertTrue(pagina.content().stream().anyMatch(p -> p.numeroAtendimento().equals("ATD-2026-0001")));
    }

    @Test
    void criaAtualizaExcluiComDocumentos() {
        limparResiduosDeTeste();
        ProntuarioRequest novo = new ProntuarioRequest(
                101L, "ATD-TESTE-9001",
                List.of(new DocumentoRequest("Documento A", null), new DocumentoRequest("Documento B", null)));
        ProntuarioDetalheResponse criado = controller.criar(novo);
        assertNotNull(criado.id());
        assertEquals(2, criado.documentos().size());
        assertEquals("ATD-TESTE-9001", criado.numeroAtendimento());

        // Atualiza: troca a lista de documentos e o número.
        ProntuarioRequest edicao = new ProntuarioRequest(
                101L, "ATD-TESTE-9002",
                List.of(new DocumentoRequest("Único documento", null)));
        ProntuarioDetalheResponse atualizado = controller.atualizar(criado.id(), edicao).getBody();
        assertNotNull(atualizado);
        assertEquals(1, atualizado.documentos().size());
        assertEquals("ATD-TESTE-9002", atualizado.numeroAtendimento());

        controller.excluir(criado.id());
        assertEquals(404, controller.buscar(criado.id()).getStatusCode().value());
    }

    @Test
    void numeroAtendimentoDeveSerUnico() {
        ProntuarioRequest req = new ProntuarioRequest(102L, "ATD-2026-0001", List.of());
        assertThrows(ResponseStatusException.class, () -> controller.criar(req));
    }
}
