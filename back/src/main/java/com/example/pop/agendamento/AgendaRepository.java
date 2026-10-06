package com.example.pop.agendamento;

import org.springframework.data.jpa.repository.JpaRepository;

/** Agendas (slots/sessões do profissional). Os pacientes marcados ficam em {@link Horario}. */
public interface AgendaRepository extends JpaRepository<Agenda, Long> {
}
