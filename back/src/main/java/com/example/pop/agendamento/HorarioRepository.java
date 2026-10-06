package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.pop.paciente.Paciente;
import com.example.pop.profissional.ProfissionalSaude;

public interface HorarioRepository extends JpaRepository<Horario, Long> {

    /** Há algum horário vinculado a este profissional? (bloqueia a exclusão do profissional). */
    boolean existsByAgenda_ProfissionalSaude_Id(Long profissionalSaudeId);

    /** Carrega um horário garantindo que é do paciente informado (escopo do app). */
    Optional<Horario> findByIdAndPaciente_Id(Long id, Long pacienteId);

    /** Horários de um paciente (app "Meus agendamentos"); a ordenação vem do Pageable. */
    Page<Horario> findByPaciente_Id(Long pacienteId, Pageable pageable);

    /**
     * Próximos horários do paciente NA UNIDADE informada (a partir de :agora), do mais próximo
     * ao mais distante. Escopado por unidade para não misturar dados de unidades diferentes.
     */
    List<Horario> findByPaciente_IdAndAgenda_UnidadeSaude_IdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
            Long pacienteId, Long unidadeId, LocalDateTime agora);

    /**
     * Dedup (chave natural) da criação via SIRESP: já existe horário do mesmo paciente, com o mesmo
     * profissional, no mesmo instante? Fecha o caso em que {@code ID_AGE_CONSULTA_HOR} vem vazio e evita duplicar
     * um horário "órfão" (criado mas cujo vínculo no registro SIRESP não chegou a ser gravado).
     */
    Optional<Horario> findFirstByPacienteAndAgenda_ProfissionalSaudeAndDataHora(
            Paciente paciente, ProfissionalSaude profissionalSaude, LocalDateTime dataHora);

    /**
     * Horários de um procedimento que estão na janela de disparo de um lembrete:
     * status ativo (informado), ainda por acontecer (dataHora >= agora) e já dentro
     * da antecedência (dataHora <= limite = agora + horas). O job filtra os que ainda
     * não dispararam pelo registro de disparo.
     */
    @Query("""
            select a from Horario a
            where a.agenda.procedimento.id = :procedimentoId
              and a.statusAgendamento in :status
              and a.dataHora >= :agora
              and a.dataHora <= :limite
            """)
    List<Horario> paraLembrete(@Param("procedimentoId") Long procedimentoId,
            @Param("status") List<StatusAgendamento> status,
            @Param("agora") LocalDateTime agora, @Param("limite") LocalDateTime limite);

    @Query(value = """
            select a from Horario a
            where (:status is null or a.statusAgendamento = :status)
              and (:nome is null or lower(a.paciente.nome) like :nome escape '\\')
              and (:especialidadeNome is null or lower(a.agenda.especialidade.nome) like :especialidadeNome escape '\\')
              and (:profissionalNome is null or lower(a.agenda.profissionalSaude.nome) like :profissionalNome escape '\\')
              and (:entregaResumo is null or a.entregaResumo = :entregaResumo)
              and a.dataHora >= coalesce(:dataDe, a.dataHora)
              and a.dataHora <= coalesce(:dataAte, a.dataHora)
              and (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
            """,
            countQuery = """
            select count(a) from Horario a
            where (:status is null or a.statusAgendamento = :status)
              and (:nome is null or lower(a.paciente.nome) like :nome escape '\\')
              and (:especialidadeNome is null or lower(a.agenda.especialidade.nome) like :especialidadeNome escape '\\')
              and (:profissionalNome is null or lower(a.agenda.profissionalSaude.nome) like :profissionalNome escape '\\')
              and (:entregaResumo is null or a.entregaResumo = :entregaResumo)
              and a.dataHora >= coalesce(:dataDe, a.dataHora)
              and a.dataHora <= coalesce(:dataAte, a.dataHora)
              and (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
            """)
    Page<Horario> search(@Param("status") StatusAgendamento status,
            @Param("nome") String nome,
            @Param("especialidadeNome") String especialidadeNome,
            @Param("profissionalNome") String profissionalNome,
            @Param("entregaResumo") EstadoEntrega entregaResumo,
            @Param("dataDe") LocalDateTime dataDe,
            @Param("dataAte") LocalDateTime dataAte,
            @Param("unidadeId") Long unidadeId,
            Pageable pageable);

    // ===================== Dashboard (agregações) =====================

    @Query("""
            select count(a) from Horario a
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            """)
    long contarPeriodo(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Horários futuros entre :de e :ate (ex.: próximos 7 dias). */
    @Query("""
            select count(a) from Horario a
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :de and :ate
            """)
    long contarEntre(@Param("unidadeId") Long unidadeId,
            @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);

    @Query("""
            select a.statusAgendamento, count(a) from Horario a
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.statusAgendamento
            """)
    List<Object[]> agruparPorStatus(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Série diária (data, quantidade) — agrupada no banco por dia. */
    @Query("""
            select cast(a.dataHora as date), count(a) from Horario a
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by cast(a.dataHora as date)
            """)
    List<Object[]> serieDiaria(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
            select a.agenda.procedimento.nome, count(a) from Horario a
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.agenda.procedimento.id, a.agenda.procedimento.nome
            order by count(a) desc, a.agenda.procedimento.nome asc
            """)
    List<Object[]> topProcedimentos(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
            select a.agenda.profissionalSaude.nome, count(a) from Horario a
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.agenda.profissionalSaude.id, a.agenda.profissionalSaude.nome
            order by count(a) desc, a.agenda.profissionalSaude.nome asc
            """)
    List<Object[]> topProfissionais(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
            select a.agenda.especialidade.nome, count(a) from Horario a
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.agenda.especialidade.id, a.agenda.especialidade.nome
            order by count(a) desc, a.agenda.especialidade.nome asc
            """)
    List<Object[]> porEspecialidade(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    @Query("""
            select mf.motivo, count(a) from Horario a join a.motivosFalta mf
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by mf.id, mf.motivo
            order by count(a) desc, mf.motivo asc
            """)
    List<Object[]> agruparMotivosFalta(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
