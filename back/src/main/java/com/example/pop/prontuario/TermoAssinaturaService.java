package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.procedimento.TermoProcedimento;
import com.example.pop.procedimento.TermoProcedimentoRepository;

/**
 * Regra de disparo dos termos (TCLE) para assinatura. Ao registrar a PRESENÇA do paciente, se o
 * procedimento do agendamento tiver termos vinculados, garante um prontuário para o atendimento e
 * gera nele uma pendência de assinatura por termo (idempotente). Sem termos no procedimento, não
 * registra nada. A assinatura em si (ZapSign) é um passo posterior.
 */
@Service
public class TermoAssinaturaService {

    private final ProntuarioRepository prontuarioRepository;
    private final TermoProcedimentoRepository termoProcedimentoRepository;
    private final TermoAssinaturaRepository termoAssinaturaRepository;

    public TermoAssinaturaService(ProntuarioRepository prontuarioRepository,
            TermoProcedimentoRepository termoProcedimentoRepository,
            TermoAssinaturaRepository termoAssinaturaRepository) {
        this.prontuarioRepository = prontuarioRepository;
        this.termoProcedimentoRepository = termoProcedimentoRepository;
        this.termoAssinaturaRepository = termoAssinaturaRepository;
    }

    /**
     * Gera as pendências de assinatura quando o agendamento está em PRESENÇA do paciente e o
     * procedimento tem termos. Idempotente: pode ser chamado a cada atualização do agendamento.
     */
    @Transactional
    public void dispararSeNecessario(Agendamento agendamento) {
        if (agendamento == null || agendamento.getStatusAgendamento() != StatusAgendamento.PRESENCA_PACIENTE) {
            return;
        }
        Long procedimentoId = agendamento.getProcedimento() == null ? null : agendamento.getProcedimento().getId();
        if (procedimentoId == null) {
            return;
        }
        List<TermoProcedimento> termos =
                termoProcedimentoRepository.findByProcedimentoIdOrderByCriadoEmDesc(procedimentoId);
        if (termos.isEmpty()) {
            return; // procedimento sem termos vinculados: não registra nada
        }
        Prontuario prontuario = obterOuCriarProntuario(agendamento);
        LocalDateTime agora = LocalDateTime.now();
        for (TermoProcedimento termo : termos) {
            if (termoAssinaturaRepository.existsByProntuario_IdAndTermoProcedimento_Id(prontuario.getId(), termo.getId())) {
                continue; // já gerado (idempotência)
            }
            TermoAssinatura pendencia = new TermoAssinatura();
            pendencia.setProntuario(prontuario);
            pendencia.setTermoProcedimento(termo);
            pendencia.setNome(termo.getNome());
            pendencia.setUrl(termo.getUrl());
            pendencia.setContentType(termo.getContentType());
            pendencia.setStatus(StatusTermoAssinatura.PENDENTE);
            pendencia.setCriadoEm(agora);
            termoAssinaturaRepository.save(pendencia);
        }
    }

    /** Reusa o prontuário do agendamento se já existir; senão cria um (número de atendimento gerado). */
    private Prontuario obterOuCriarProntuario(Agendamento agendamento) {
        return prontuarioRepository.findFirstByAgendamento_Id(agendamento.getId())
                .orElseGet(() -> {
                    Prontuario p = new Prontuario();
                    p.setAgendamento(agendamento);
                    p.setNumeroAtendimento(gerarNumeroAtendimento(agendamento.getId()));
                    return prontuarioRepository.save(p);
                });
    }

    /** Número de atendimento único e estável por agendamento (ex.: "AT-123"); evita colisão. */
    private String gerarNumeroAtendimento(Long agendamentoId) {
        String base = "AT-" + agendamentoId;
        String candidato = base;
        int sufixo = 1;
        while (prontuarioRepository.existsByNumeroAtendimento(candidato)) {
            candidato = base + "-" + sufixo++;
        }
        return candidato;
    }
}
