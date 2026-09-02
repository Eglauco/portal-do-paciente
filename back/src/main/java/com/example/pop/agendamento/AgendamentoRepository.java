package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.util.List;
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

    // ===================== Dashboard (agregações) =====================

    @Query("""
            select count(a) from Agendamento a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            """)
    long contarPeriodo(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Agendamentos futuros entre :de e :ate (ex.: próximos 7 dias). */
    @Query("""
            select count(a) from Agendamento a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.dataHora between :de and :ate
            """)
    long contarEntre(@Param("unidadeId") Long unidadeId,
            @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);

    @Query("""
            select a.statusAgendamento, count(a) from Agendamento a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.statusAgendamento
            """)
    List<Object[]> agruparPorStatus(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Série diária (data, quantidade) — agrupada no banco por dia. */
    @Query("""
            select cast(a.dataHora as date), count(a) from Agendamento a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by cast(a.dataHora as date)
            """)
    List<Object[]> serieDiaria(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
            select a.procedimento.nome, count(a) from Agendamento a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.procedimento.id, a.procedimento.nome
            order by count(a) desc, a.procedimento.nome asc
            """)
    List<Object[]> topProcedimentos(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
            select a.profissionalSaude.nome, count(a) from Agendamento a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.profissionalSaude.id, a.profissionalSaude.nome
            order by count(a) desc, a.profissionalSaude.nome asc
            """)
    List<Object[]> topProfissionais(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
            select a.especialidade.nome, count(a) from Agendamento a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.especialidade.id, a.especialidade.nome
            order by count(a) desc, a.especialidade.nome asc
            """)
    List<Object[]> porEspecialidade(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
            select mf.motivo, count(a) from Agendamento a join a.motivosFalta mf
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by mf.id, mf.motivo
            order by count(a) desc, mf.motivo asc
            """)
    List<Object[]> agruparMotivosFalta(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
