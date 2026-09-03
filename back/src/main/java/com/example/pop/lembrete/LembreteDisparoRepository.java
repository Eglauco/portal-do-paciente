package com.example.pop.lembrete;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LembreteDisparoRepository extends JpaRepository<LembreteDisparo, Long> {

    /** Já disparou este lembrete para este agendamento? (dispara 1x por agendamento) */
    boolean existsByLembreteIdAndAgendamentoId(Long lembreteId, Long agendamentoId);
}
