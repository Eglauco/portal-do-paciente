package com.example.pop.nps;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpsRepository extends JpaRepository<Nps, Long> {

    boolean existsByAgendamentoId(Long agendamentoId);

    Optional<Nps> findByAgendamentoId(Long agendamentoId);

    @Query("""
            select n from Nps n
            where (:status is null or n.status = :status)
              and (:pacienteId is null or n.agendamento.paciente.id = :pacienteId)
              and (:unidadeId is null or n.agendamento.unidadeSaude.id = :unidadeId)
            """)
    Page<Nps> search(
            @Param("status") StatusNps status,
            @Param("pacienteId") Long pacienteId,
            @Param("unidadeId") Long unidadeId,
            Pageable pageable);
}
