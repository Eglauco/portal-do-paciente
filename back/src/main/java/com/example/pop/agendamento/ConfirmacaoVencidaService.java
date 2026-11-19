package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Regra da CONFIRMAÇÃO VENCIDA: em cada unidade com o prazo ligado ({@code minutosConfirmacaoVencida > 0}),
 * todo horário ainda "Aguardando confirmação do paciente" cuja data/hora + prazo já passou vira
 * "Confirmação vencida". Vale também para os antigos (backlog) na 1ª rodada após ligar. Sem aviso ao
 * paciente; a troca fica no histórico do horário com autor SISTEMA. Rodada pelo
 * {@link ConfirmacaoVencidaScheduler} a cada minuto, uma vez por inquilino (tenant já fixado).
 */
@Service
public class ConfirmacaoVencidaService {

    /** Data/hora do horário é hora local do Brasil (LocalDateTime sem fuso) — compara no mesmo fuso. */
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final UnidadeRepository unidadeRepository;
    private final HorarioRepository horarioRepository;
    private final HorarioLogService logService;

    public ConfirmacaoVencidaService(UnidadeRepository unidadeRepository, HorarioRepository horarioRepository,
            HorarioLogService logService) {
        this.unidadeRepository = unidadeRepository;
        this.horarioRepository = horarioRepository;
        this.logService = logService;
    }

    /** Aplica a regra no inquilino atual. Devolve quantos horários venceram nesta rodada. */
    @Transactional
    public int vencerConfirmacoes() {
        return vencerConfirmacoes(LocalDateTime.now(FUSO));
    }

    /** Versão com o "agora" explícito (determinística para teste). */
    @Transactional
    public int vencerConfirmacoes(LocalDateTime agora) {
        int vencidos = 0;
        for (Unidade unidade : unidadeRepository.findByMinutosConfirmacaoVencidaGreaterThan(0)) {
            LocalDateTime limite = limite(agora, unidade.getMinutosConfirmacaoVencida());
            List<Long> ids = horarioRepository.idsParaVencer(unidade.getId(),
                    StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE, limite);
            for (Long id : ids) {
                // Condicional: se o paciente confirmou neste mesmo instante, não sobrescreve.
                if (horarioRepository.trocarStatusSe(id, StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE,
                        StatusAgendamento.CONFIRMACAO_VENCIDA) == 1) {
                    logService.registrarDoSistema(horarioRepository.getReferenceById(id),
                            StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE, StatusAgendamento.CONFIRMACAO_VENCIDA);
                    vencidos++;
                }
            }
        }
        return vencidos;
    }

    /**
     * Data/hora máxima de um horário para já estar vencido: {@code agora − minutos}. Ex.: prazo 60 e agora
     * 11:00 → vence quem é de 10:00 ou antes. Função pura.
     */
    static LocalDateTime limite(LocalDateTime agora, int minutos) {
        return agora.minusMinutes(minutos);
    }
}
