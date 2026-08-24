package com.example.pop.agendamento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @Query(value = """
            select a from Agendamento a
            where (:status is null or a.statusAgendamento = :status)
              and (:pacienteId is null or a.paciente.id = :pacienteId)
            """,
            countQuery = """
            select count(a) from Agendamento a
            where (:status is null or a.statusAgendamento = :status)
              and (:pacienteId is null or a.paciente.id = :pacienteId)
            """)
    Page<Agendamento> search(@Param("status") StatusAgendamento status, @Param("pacienteId") Long pacienteId,
            Pageable pageable);
}
