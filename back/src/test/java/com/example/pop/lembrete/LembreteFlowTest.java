package com.example.pop.lembrete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.pop.agendamento.AgendamentoController;
import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.agendamento.AgendamentoRequest;
import com.example.pop.agendamento.AgendamentoResponse;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.notificacao.Notificacao;
import com.example.pop.notificacao.NotificacaoRepository;
import com.example.pop.notificacao.TipoNotificacao;
import com.example.pop.procedimento.Procedimento;
import com.example.pop.procedimento.ProcedimentoController;
import com.example.pop.procedimento.ProcedimentoRepository;
import com.example.pop.push.PushService;

@SpringBootTest
class LembreteFlowTest {

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
    private LembreteController lembreteController;
    @Autowired
    private LembreteService lembreteService;
    @Autowired
    private LembreteRepository lembreteRepository;
    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @MockitoBean
    private PushService pushService;

    @Test
    void disparaUmaVezGeraPopupComCancelamentoEReconhece() {
        // Procedimento com prazo de cancelamento de 1h (para o pop-up poder cancelar).
        Procedimento proc = procedimentoController.criar(new Procedimento(null, "Proc Lembrete Teste", null, 1));

        // Agendamento CONFIRMADO daqui a 2h nesse procedimento (paciente/unidade/etc. do seed = id 1).
        LocalDateTime dh = LocalDateTime.now(FUSO).plusHours(2).withNano(0);
        AgendamentoResponse ag = agendamentoController.criar(
                new AgendamentoRequest(dh, 1L, 1L, proc.getId(), 1L, 1L, null));
        Long pacienteId = ag.paciente().id();
        // criar() sempre nasce AGUARDANDO; confirmamos para o pop-up poder cancelar.
        var entidade = agendamentoRepository.findById(ag.id()).orElseThrow();
        entidade.setStatusAgendamento(StatusAgendamento.PACIENTE_CONFIRMOU);
        agendamentoRepository.save(entidade);

        // Lembrete de 24h de antecedência: o agendamento (em 2h) já está na janela.
        LembreteResponse lem = lembreteController.criar(proc.getId(),
                new LembreteRequest("Sua consulta é em breve.", 24));

        Long notifId = null;
        try {
            lembreteService.dispararPendentes();

            // Gerou uma notificação de lembrete apontando para o agendamento.
            List<Notificacao> notifs = notificacaoRepository
                    .findByPacienteIdAndTipoAndLidaFalseOrderByCriadoEmDesc(pacienteId, TipoNotificacao.LEMBRETE);
            assertTrue(notifs.stream().anyMatch(n -> ag.id().equals(n.getReferenciaId())));

            // Pop-up pendente com opção de cancelar (confirmado + dentro do prazo).
            LembretePopupResponse popup = lembreteService.popupsPendentes(pacienteId).stream()
                    .filter(p -> ag.id().equals(p.agendamentoId())).findFirst().orElseThrow();
            notifId = popup.id();
            assertEquals("Sua consulta é em breve.", popup.mensagem());
            assertTrue(popup.podeCancelar());

            // Redisparar não cria um 2º disparo para (lembrete, agendamento).
            lembreteService.dispararPendentes();
            long doAg = lembreteService.popupsPendentes(pacienteId).stream()
                    .filter(p -> ag.id().equals(p.agendamentoId())).count();
            assertEquals(1, doAg);

            // Reconhecer some com o pop-up (marca a notificação como lida).
            lembreteService.reconhecer(popup.id(), pacienteId);
            assertFalse(lembreteService.popupsPendentes(pacienteId).stream()
                    .anyMatch(p -> ag.id().equals(p.agendamentoId())));
        } finally {
            agendamentoRepository.deleteById(ag.id()); // remove o disparo por cascade
            lembreteRepository.deleteById(lem.id());
            procedimentoRepository.deleteById(proc.getId());
            if (notifId != null) {
                notificacaoRepository.deleteById(notifId);
            }
        }
    }
}
