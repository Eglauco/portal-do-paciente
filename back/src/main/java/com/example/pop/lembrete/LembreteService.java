package com.example.pop.lembrete;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.notificacao.Notificacao;
import com.example.pop.notificacao.NotificacaoRepository;
import com.example.pop.notificacao.NotificacaoService;
import com.example.pop.notificacao.TipoNotificacao;
import com.example.pop.push.PushService;

/**
 * Regras dos lembretes: dispara (job periódico) push + notificação para os
 * agendamentos que entraram na antecedência configurada, e monta os pop-ups
 * pendentes do paciente (com a opção de cancelar quando ainda dá tempo).
 */
@Service
public class LembreteService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String TITULO = "Lembrete de agendamento";
    /** Só agendamentos ativos e por acontecer (ignora cancelado/falta/presença). */
    private static final List<StatusAgendamento> ATIVOS = List.of(
            StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE,
            StatusAgendamento.PACIENTE_CONFIRMOU);

    private final LembreteRepository lembreteRepository;
    private final LembreteDisparoRepository disparoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoService notificacaoService;
    private final PushService pushService;

    public LembreteService(LembreteRepository lembreteRepository, LembreteDisparoRepository disparoRepository,
            AgendamentoRepository agendamentoRepository, NotificacaoRepository notificacaoRepository,
            NotificacaoService notificacaoService, PushService pushService) {
        this.lembreteRepository = lembreteRepository;
        this.disparoRepository = disparoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.notificacaoService = notificacaoService;
        this.pushService = pushService;
    }

    /**
     * Dispara os lembretes cujos agendamentos ativos estão na janela [agora, agora +
     * horas] e ainda não foram disparados. Grava o disparo (dedup) + a notificação na
     * mesma transação; o push vai depois do commit. Retorna quantos foram disparados.
     */
    @Transactional
    public int dispararPendentes() {
        LocalDateTime agora = LocalDateTime.now(FUSO);
        List<Runnable> pushes = new ArrayList<>();
        for (Lembrete l : lembreteRepository.findAll()) {
            LocalDateTime limite = agora.plusHours(l.getHorasAntecedencia());
            for (Agendamento a : agendamentoRepository.paraLembrete(l.getProcedimento().getId(), ATIVOS, agora, limite)) {
                if (disparoRepository.existsByLembreteIdAndAgendamentoId(l.getId(), a.getId())) {
                    continue;
                }
                LembreteDisparo d = new LembreteDisparo();
                d.setLembrete(l);
                d.setAgendamento(a);
                d.setCriadoEm(agora);
                disparoRepository.save(d);
                notificacaoService.registrarParaPaciente(a.getPaciente(), TipoNotificacao.LEMBRETE, TITULO, l.getTexto(), a.getId());
                Long pacienteId = a.getPaciente().getId();
                Long agendamentoId = a.getId();
                String texto = l.getTexto();
                pushes.add(() -> pushService.notificarPaciente(pacienteId, TITULO, texto,
                        Map.of("tipo", "LEMBRETE", "agendamentoId", agendamentoId)));
            }
        }
        aposCommit(() -> pushes.forEach(Runnable::run));
        return pushes.size();
    }

    /** Pop-ups de lembrete ainda não reconhecidos pelo paciente (não lidos). */
    @Transactional(readOnly = true)
    public List<LembretePopupResponse> popupsPendentes(Long pacienteId) {
        LocalDateTime agora = LocalDateTime.now(FUSO);
        return notificacaoRepository
                .findByPacienteIdAndTipoAndLidaFalseOrderByCriadoEmDesc(pacienteId, TipoNotificacao.LEMBRETE)
                .stream().map(n -> toPopup(n, agora)).toList();
    }

    private LembretePopupResponse toPopup(Notificacao n, LocalDateTime agora) {
        Long agendamentoId = n.getReferenciaId();
        Agendamento a = agendamentoId == null ? null : agendamentoRepository.findById(agendamentoId).orElse(null);
        boolean podeCancelar = a != null && podeCancelar(a, agora);
        LocalDateTime dataHora = a != null ? a.getDataHora() : null;
        String especialidade = a != null && a.getEspecialidade() != null ? a.getEspecialidade().getNome() : null;
        return new LembretePopupResponse(n.getId(), n.getTitulo(), n.getCorpo(), agendamentoId,
                podeCancelar, dataHora, especialidade);
    }

    /** Mesma regra do cancelamento pelo paciente: confirmado e dentro do prazo. */
    private boolean podeCancelar(Agendamento a, LocalDateTime agora) {
        if (a.getStatusAgendamento() != StatusAgendamento.PACIENTE_CONFIRMOU) {
            return false;
        }
        Integer horas = a.getProcedimento().getHorasCancelamento();
        return horas == null || !agora.isAfter(a.getDataHora().minusHours(horas));
    }

    /** Reconhece o pop-up (marca a notificação como lida → não reaparece). */
    @Transactional
    public void reconhecer(Long notificacaoId, Long pacienteId) {
        notificacaoService.marcarLida(notificacaoId, pacienteId);
    }

    /** Executa após o commit da transação atual (ou imediatamente, se não houver). */
    private void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }
}
