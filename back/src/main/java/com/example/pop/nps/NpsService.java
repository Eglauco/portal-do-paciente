package com.example.pop.nps;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.StatusAgendamento;

@Service
public class NpsService {

    private final NpsRepository repository;

    public NpsService(NpsRepository repository) {
        this.repository = repository;
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
        repository.save(nps);
    }
}
