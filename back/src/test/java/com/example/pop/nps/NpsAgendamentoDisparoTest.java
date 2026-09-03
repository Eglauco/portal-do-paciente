package com.example.pop.nps;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.pop.agendamento.AgendamentoController;
import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.agendamento.AgendamentoRequest;
import com.example.pop.agendamento.AgendamentoResponse;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.procedimento.Procedimento;
import com.example.pop.procedimento.ProcedimentoController;
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.push.PushService;

@SpringBootTest
class NpsAgendamentoDisparoTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private AgendamentoController agendamentoController;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private ProcedimentoController procedimentoController;
    @Autowired
    private ProcedimentoRepository procedimentoRepository;
    @Autowired
    private NpsService npsService;
    @Autowired
    private NpsRepository npsRepository;

    @MockitoBean
    private PushService pushService;

    /** Cria um agendamento (seed id 1 para especialidade/profissional/paciente/unidade) e marca presença. */
    private AgendamentoResponse presenca(Long procId) {
        LocalDateTime dh = LocalDateTime.now(FUSO).plusDays(1).withNano(0);
        AgendamentoResponse ag = agendamentoController.criar(new AgendamentoRequest(dh, 1L, 1L, procId, 1L, 1L, null));
        agendamentoController.atualizar(ag.id(),
                new AgendamentoRequest(dh, 1L, 1L, procId, 1L, 1L, StatusAgendamento.PRESENCA_PACIENTE));
        return ag;
    }

    private void limpar(Long agId, Long procId) {
        npsRepository.findByAgendamentoId(agId).ifPresent(n -> npsRepository.deleteById(n.getId()));
        agendamentoRepository.deleteById(agId);
        procedimentoRepository.deleteById(procId);
    }

    @Test
    void horasZeroDisparaNaHora() {
        Procedimento proc = procedimentoController.criar(new Procedimento(null, "Proc NPS 0h", null, 24, 0));
        AgendamentoResponse ag = presenca(proc.getId());
        try {
            Nps nps = npsRepository.findByAgendamentoId(ag.id()).orElseThrow();
            assertNotNull(nps.getDisparadoEm()); // 0h: já disparado na presença
            verify(pushService).notificarNpsPendente(argThat(n -> n.getId().equals(nps.getId())));
        } finally {
            limpar(ag.id(), proc.getId());
        }
    }

    @Test
    void horasPositivasAgendaEDisparaNoHorario() {
        Procedimento proc = procedimentoController.criar(new Procedimento(null, "Proc NPS 2h", null, 24, 2));
        AgendamentoResponse ag = presenca(proc.getId());
        try {
            Nps nps = npsRepository.findByAgendamentoId(ag.id()).orElseThrow();
            assertNull(nps.getDisparadoEm()); // agendado, ainda não disparado
            assertNotNull(nps.getDispararEm());

            // Antes da hora: não dispara.
            npsService.dispararAgendados();
            assertNull(npsRepository.findById(nps.getId()).orElseThrow().getDisparadoEm());

            // Chegou a hora: dispara (agendamento ainda em presença).
            nps.setDispararEm(LocalDateTime.now(FUSO).minusMinutes(1));
            npsRepository.save(nps);
            npsService.dispararAgendados();
            assertNotNull(npsRepository.findById(nps.getId()).orElseThrow().getDisparadoEm());
            verify(pushService).notificarNpsPendente(argThat(n -> n.getId().equals(nps.getId())));
        } finally {
            limpar(ag.id(), proc.getId());
        }
    }

    @Test
    void cancelaSeSaiuDePresencaAntesDeDisparar() {
        Procedimento proc = procedimentoController.criar(new Procedimento(null, "Proc NPS cancela", null, 24, 2));
        AgendamentoResponse ag = presenca(proc.getId());
        Long procId = proc.getId();
        try {
            Nps nps = npsRepository.findByAgendamentoId(ag.id()).orElseThrow();
            // Sai de presença (ex.: correção para falta).
            LocalDateTime dh = LocalDateTime.now(FUSO).plusDays(1).withNano(0);
            agendamentoController.atualizar(ag.id(),
                    new AgendamentoRequest(dh, 1L, 1L, procId, 1L, 1L, StatusAgendamento.FALTA_PACIENTE));

            nps.setDispararEm(LocalDateTime.now(FUSO).minusMinutes(1));
            npsRepository.save(nps);
            npsService.dispararAgendados();

            // Não está mais em presença: o NPS agendado foi cancelado (removido).
            assertTrue(npsRepository.findByAgendamentoId(ag.id()).isEmpty());
        } finally {
            limpar(ag.id(), procId);
        }
    }
}
