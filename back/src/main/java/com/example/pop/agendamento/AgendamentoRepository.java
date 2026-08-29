package com.example.pop.agendamento;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    /** Carrega um agendamento garantindo que é do paciente informado (escopo do app). */
    Optional<Agendamento> findByIdAndPacienteId(Long id, Long pacienteId);

    @Query(value = """
            select a from Agendamento a
            where (:status is null or a.statusAgendamento = :status)
              and (:pacienteId is null or a.paciente.id = :pacienteId)
              and (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
            """,
            countQuery = """
            select count(a) from Agendamento a
            where (:status is null or a.statusAgendamento = :status)
              and (:pacienteId is null or a.paciente.id = :pacienteId)
              and (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
            """)
    Page<Agendamento> search(@Param("status") StatusAgendamento status, @Param("pacienteId") Long pacienteId,
            @Param("unidadeId") Long unidadeId, Pageable pageable);
}
