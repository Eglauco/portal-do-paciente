package com.example.pop.nps;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.push.PushService;

@Service
public class NpsService {

    private final NpsRepository repository;
    private final PushService pushService;

    public NpsService(NpsRepository repository, PushService pushService) {
        this.repository = repository;
        this.pushService = pushService;
    }

    /**
     * Gera um NPS (pendente) vinculado ao agendamento quando o status for
     * PRESENCA_PACIENTE e ainda não existir um NPS para ele.
     */
    public void gerarSeNecessario(Agendamento agendamento) {
        if (agendamento.getStatusAgendamento() != StatusAgendamento.PRESENCA_PACIENTE) {
            return;
        }
        if (repository.existsByAgendamentoId(agendamento.getId())) {
            return;
        }
        Nps nps = new Nps();
        nps.setAgendamento(agendamento);
        nps.setStatus(StatusNps.PENDENTE);
        nps.setCriadoEm(LocalDateTime.now());
        Nps salvo = repository.save(nps);
        // Notifica o paciente para avaliar o atendimento.
        pushService.notificarNpsPendente(salvo);
    }
}
