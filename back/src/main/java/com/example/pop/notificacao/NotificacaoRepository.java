package com.example.pop.notificacao;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /** Notificações do paciente, mais recentes primeiro. */
    Page<Notificacao> findByPacienteIdOrderByCriadoEmDesc(Long pacienteId, Pageable pageable);

    /** Quantas ainda não foram lidas (para o contador do sino). */
    long countByPacienteIdAndLidaFalse(Long pacienteId);

    /** Carrega garantindo que a notificação é do paciente logado (escopo do app). */
    Optional<Notificacao> findByIdAndPacienteId(Long id, Long pacienteId);
}
