package com.example.pop.sau;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.common.Pagina;

@SpringBootTest
class TipoManifestacaoControllerTest {

    @Autowired
    private TipoManifestacaoController controller;
    @Autowired
    private MeuSauController meuController;
    @Autowired
    private TipoManifestacaoRepository repository;

    private final List<Long> criados = new ArrayList<>();

    @AfterEach
    void limpar() {
        criados.forEach(id -> repository.findById(id).ifPresent(t -> repository.deleteById(id)));
        criados.clear();
    }

    private Long criar(String nome, String descricao, Boolean ativo) {
        TipoManifestacaoResponse t = controller.criar(new TipoManifestacaoRequest(nome, descricao, ativo));
        criados.add(t.id());
        return t.id();
    }

    @Test
    void criaListaEBuscaPorNome() {
        Long id = criar("Reclamação Teste", "Descreva o problema.", null);
        assertNotNull(id);

        TipoManifestacaoResponse buscado = controller.buscar(id).getBody();
        assertNotNull(buscado);
        assertEquals("Reclamação Teste", buscado.nome());
        assertEquals("Descreva o problema.", buscado.descricao());
        assertTrue(buscado.ativo(), "novo tipo nasce ativo por padrão");

        Pagina<TipoManifestacaoResponse> pagina = controller.listar("Reclamação Teste", null, 0, 20);
        assertTrue(pagina.content().stream().anyMatch(t -> t.id().equals(id)));
    }

    @Test
    void desativarRemoveDaSelecaoDoPaciente() {
        Long id = criar("Denúncia Teste", null, true);
        assertTrue(meuController.tipos().stream().anyMatch(t -> t.id().equals(id)));

        controller.atualizar(id, new TipoManifestacaoRequest("Denúncia Teste", null, false));

        // Some da seleção do paciente (só ativos), mas continua existindo no cadastro.
        assertFalse(meuController.tipos().stream().anyMatch(t -> t.id().equals(id)));
        assertFalse(controller.buscar(id).getBody().ativo());
    }

    @Test
    void excluiTipoSemUso() {
        Long id = criar("Efêmero Teste", null, null);
        assertEquals(204, controller.excluir(id).getStatusCode().value());
        assertEquals(404, controller.buscar(id).getStatusCode().value());
        criados.remove(id);
    }

    @Test
    void filtraApenasAtivos() {
        Long ativo = criar("Ativo Teste", null, true);
        Long inativo = criar("Inativo Teste", null, false);

        Pagina<TipoManifestacaoResponse> apenasAtivos = controller.listar(null, true, 0, 100);
        assertTrue(apenasAtivos.content().stream().anyMatch(t -> t.id().equals(ativo)));
        assertFalse(apenasAtivos.content().stream().anyMatch(t -> t.id().equals(inativo)));
    }
}
