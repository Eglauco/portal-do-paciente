package com.example.pop.nps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import com.example.pop.agendamento.AgendamentoController;
import com.example.pop.agendamento.AgendamentoRequest;
import com.example.pop.agendamento.AgendamentoResponse;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.common.Pagina;

@SpringBootTest
class NpsControllerTest {

    @Autowired
    private NpsController controller;

    @Autowired
    private AgendamentoController agendamentoController;

    @Autowired
    private NpsRepository repository;

    @Test
    void listaContemRegistrosSemeados() {
        Pagina<NpsResponse> pagina = controller.listar(null, null, null, 0, 10);
        assertTrue(pagina.totalElements() >= 8, "esperado ao menos os NPS semeados");
        assertNotNull(pagina.content().get(0).paciente());
        assertNotNull(pagina.content().get(0).unidadeSaude());
    }

    @Test
    void filtraPorStatus() {
        Pagina<NpsResponse> pagina = controller.listar(StatusNps.RESPONDIDO, null, null, 0, 50);
        assertTrue(pagina.totalElements() >= 1);
        pagina.content().forEach(r -> assertEquals(StatusNps.RESPONDIDO, r.status()));
    }

    @Test
    void classificacaoDerivadaDaNota() {
        assertEquals("Promotor", NpsResponse.classificacao(10));
        assertEquals("Promotor", NpsResponse.classificacao(9));
        assertEquals("Neutro", NpsResponse.classificacao(8));
        assertEquals("Neutro", NpsResponse.classificacao(7));
        assertEquals("Detrator", NpsResponse.classificacao(6));
        assertEquals("Detrator", NpsResponse.classificacao(0));
        assertNull(NpsResponse.classificacao(null));
    }

    @Test
    void presencaDoPacienteGeraNpsEPermiteResponder() {
        // Cria um agendamento (nasce AGUARDANDO) e o move para PRESENCA_PACIENTE.
        AgendamentoRequest novo = new AgendamentoRequest(
                LocalDateTime.of(2026, 11, 5, 9, 0), 1L, 1L, 1L, 1L, 1L, null);
        AgendamentoResponse criado = agendamentoController.criar(novo);

        AgendamentoRequest presenca = new AgendamentoRequest(
                LocalDateTime.of(2026, 11, 5, 9, 0), 1L, 1L, 1L, 1L, 1L,
                StatusAgendamento.PRESENCA_PACIENTE);
        agendamentoController.atualizar(criado.id(), presenca);

        Nps gerado = repository.findByAgendamentoId(criado.id()).orElseThrow();
        assertEquals(StatusNps.PENDENTE, gerado.getStatus());
        assertNull(gerado.getNota());

        // Responde o NPS.
        ResponseEntity<NpsDetalheResponse> resposta = controller.responder(
                gerado.getId(), new ResponderNpsRequest(9, "Muito bom!"));
        NpsDetalheResponse corpo = resposta.getBody();
        assertNotNull(corpo);
        assertEquals(StatusNps.RESPONDIDO, corpo.status());
        assertEquals(9, corpo.nota());
        assertEquals("Promotor", corpo.classificacao());
        assertNotNull(corpo.respondidoEm());

        // Limpa os registros criados no teste.
        repository.deleteById(gerado.getId());
        agendamentoController.excluir(criado.id());
    }

    @Test
    void naoGeraNpsDuplicado() {
        AgendamentoRequest novo = new AgendamentoRequest(
                LocalDateTime.of(2026, 11, 6, 10, 0), 1L, 1L, 1L, 1L, 1L,
                StatusAgendamento.PRESENCA_PACIENTE);
        AgendamentoResponse criado = agendamentoController.criar(novo);
        // criar sempre nasce AGUARDANDO; move para presença duas vezes.
        AgendamentoRequest presenca = new AgendamentoRequest(
                LocalDateTime.of(2026, 11, 6, 10, 0), 1L, 1L, 1L, 1L, 1L,
                StatusAgendamento.PRESENCA_PACIENTE);
        agendamentoController.atualizar(criado.id(), presenca);
        agendamentoController.atualizar(criado.id(), presenca);

        long total = repository.findAll().stream()
                .filter(n -> n.getAgendamento().getId().equals(criado.id()))
                .count();
        assertEquals(1, total, "deve existir apenas um NPS por agendamento");

        repository.findByAgendamentoId(criado.id()).ifPresent(n -> repository.deleteById(n.getId()));
        agendamentoController.excluir(criado.id());
    }
}
