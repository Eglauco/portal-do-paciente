package com.example.pop.notificacao;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /** Notificações do paciente, mais recentes primeiro. */
    Page<Notificacao> findByPacienteIdOrderByCriadoEmDesc(Long pacienteId, Pageable pageable);

    /** Quantas ainda não foram lidas (para o contador do sino). */
    long countByPacienteIdAndLidaFalse(Long pacienteId);

    /** Marca TODAS as não lidas do paciente como lidas de uma vez (botão "marcar todas"). */
    @Modifying
    @Query("update Notificacao n set n.lida = true, n.lidaEm = :agora "
            + "where n.paciente.id = :pacienteId and n.lida = false")
    int marcarTodasLidas(@Param("pacienteId") Long pacienteId, @Param("agora") LocalDateTime agora);

    /** Carrega garantindo que a notificação é do paciente logado (escopo do app). */
    Optional<Notificacao> findByIdAndPacienteId(Long id, Long pacienteId);

    /** Notificações de um tipo ainda não lidas (ex.: lembretes pendentes de pop-up). */
    java.util.List<Notificacao> findByPacienteIdAndTipoAndLidaFalseOrderByCriadoEmDesc(Long pacienteId, TipoNotificacao tipo);
}
