package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.agendamento.Horario;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.configuracaoagenda.TermoConfiguracaoAgenda;
import com.example.pop.configuracaoagenda.TermoConfiguracaoAgendaRepository;

/**
 * Regra de disparo dos termos (TCLE) para assinatura. Ao registrar a PRESENÇA do paciente, se o
 * configuracaoAgenda do agendamento tiver termos vinculados, garante um prontuário para o atendimento e
 * gera nele uma pendência de assinatura por termo (idempotente). Sem termos no configuracaoAgenda, não
 * registra nada. A assinatura em si (ZapSign) é um passo posterior.
 */
@Service
public class TermoAssinaturaService {

    private final ProntuarioRepository prontuarioRepository;
    private final TermoConfiguracaoAgendaRepository termoConfiguracaoAgendaRepository;
    private final TermoAssinaturaRepository termoAssinaturaRepository;

    public TermoAssinaturaService(ProntuarioRepository prontuarioRepository,
            TermoConfiguracaoAgendaRepository termoConfiguracaoAgendaRepository,
            TermoAssinaturaRepository termoAssinaturaRepository) {
        this.prontuarioRepository = prontuarioRepository;
        this.termoConfiguracaoAgendaRepository = termoConfiguracaoAgendaRepository;
        this.termoAssinaturaRepository = termoAssinaturaRepository;
    }

    /**
     * Gera as pendências de assinatura quando o agendamento está em PRESENÇA do paciente e o
     * configuracaoAgenda tem termos. Idempotente: pode ser chamado a cada atualização do agendamento.
     */
    @Transactional
    public void dispararSeNecessario(Horario agendamento) {
        if (agendamento == null || agendamento.getStatusAgendamento() != StatusAgendamento.PRESENCA_PACIENTE) {
            return;
        }
        Long configuracaoAgendaId = agendamento.getConfiguracaoAgenda() == null ? null : agendamento.getConfiguracaoAgenda().getId();
        if (configuracaoAgendaId == null) {
            return;
        }
        List<TermoConfiguracaoAgenda> termos =
                termoConfiguracaoAgendaRepository.findByConfiguracaoAgendaIdOrderByCriadoEmDesc(configuracaoAgendaId);
        if (termos.isEmpty()) {
            return; // configuracaoAgenda sem termos vinculados: não registra nada
        }
        Prontuario prontuario = obterOuCriarProntuario(agendamento);
        LocalDateTime agora = LocalDateTime.now();
        for (TermoConfiguracaoAgenda termo : termos) {
            if (termoAssinaturaRepository.existsByProntuario_IdAndTermoConfiguracaoAgenda_Id(prontuario.getId(), termo.getId())) {
                continue; // já gerado (idempotência)
            }
            TermoAssinatura pendencia = new TermoAssinatura();
            pendencia.setProntuario(prontuario);
            pendencia.setTermoConfiguracaoAgenda(termo);
            pendencia.setNome(termo.getNome());
            pendencia.setUrl(termo.getUrl());
            pendencia.setContentType(termo.getContentType());
            pendencia.setProfissionalAssina(termo.isProfissionalAssina()); // congela a regra de coassinatura
            pendencia.setProfissionalCertificado(termo.isProfissionalCertificado());
            pendencia.setStatus(StatusTermoAssinatura.PENDENTE);
            pendencia.setCriadoEm(agora);
            termoAssinaturaRepository.save(pendencia);
        }
    }

    /** Reusa o prontuário do agendamento se já existir; senão cria um (número de atendimento gerado). */
    private Prontuario obterOuCriarProntuario(Horario agendamento) {
        return prontuarioRepository.findFirstByHorario_Id(agendamento.getId())
                .orElseGet(() -> {
                    Prontuario p = new Prontuario();
                    p.setHorario(agendamento);
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
