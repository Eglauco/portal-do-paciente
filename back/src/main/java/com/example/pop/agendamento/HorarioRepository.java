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

    /** Horários (pacientes marcados) de uma Agenda, do mais cedo ao mais tarde — para o detalhe da agenda. */
    List<Horario> findByAgenda_IdOrderByDataHoraAsc(Long agendaId);

    /** Quantos horários uma agenda tem (trava a exclusão da agenda com pacientes). */
    long countByAgenda_Id(Long agendaId);

    /** Contagem de horários por agenda (para a lista de agendas, evitando N+1). */
    @Query("select h.agenda.id, count(h) from Horario h where h.agenda.id in :ids group by h.agenda.id")
    List<Object[]> contarPorAgendas(@Param("ids") List<Long> ids);

    /**
     * Próximos horários do paciente NA UNIDADE informada (a partir de :agora), do mais próximo
     * ao mais distante. Escopado por unidade para não misturar dados de unidades diferentes.
     */
    List<Horario> findByPaciente_IdAndAgenda_UnidadeSaude_IdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
            Long pacienteId, Long unidadeId, LocalDateTime agora);

    /**
     * Dedup direto da criação via SIRESP: já existe um horário gravado com este código do CROSS DENTRO do tipo
     * (consulta/exame)? É o caminho mais robusto (não depende do estado do registro SIRESP) e evita tentar inserir um
     * código duplicado (que bateria no índice único {@code uk_horario_codigo_integracao}). O tipo é obrigatório na
     * chave porque os id-spaces do CROSS (ID_AGE_CONSULTA_HOR × ID_AGE_EXAME_HOR) podem colidir numericamente.
     */
    Optional<Horario> findFirstByCodigoIntegracaoAndTipoAtendimento(String codigoIntegracao,
            TipoAtendimento tipoAtendimento);

    /**
     * Dedup (chave natural) da criação via SIRESP: horário do mesmo paciente + profissional + instante, do MESMO
     * tipo de atendimento (ou manual, sem tipo). O filtro por tipo evita uma CONSULTA casar com um EXAME que caia no
     * mesmo minuto. Fecha o caso de código vazio (órfão). Ordenado por id — o chamador pega o primeiro.
     */
    @Query("""
            select h from Horario h
            where h.paciente = :paciente
              and h.agenda.profissionalSaude = :profissional
              and h.dataHora = :dataHora
              and (h.tipoAtendimento is null or h.tipoAtendimento = :tipo)
            order by h.id
            """)
    List<Horario> buscarPorChaveNatural(@Param("paciente") Paciente paciente,
            @Param("profissional") ProfissionalSaude profissional,
            @Param("dataHora") LocalDateTime dataHora, @Param("tipo") TipoAtendimento tipo);

    /**
     * Horários de um configuracaoAgenda que estão na janela de disparo de um lembrete:
     * status ativo (informado), ainda por acontecer (dataHora >= agora) e já dentro
     * da antecedência (dataHora <= limite = agora + horas). O job filtra os que ainda
     * não dispararam pelo registro de disparo.
     */
    @Query("""
            select a from Horario a
            where a.agenda.configuracaoAgenda.id = :configuracaoAgendaId
              and a.statusAgendamento in :status
              and a.dataHora >= :agora
              and a.dataHora <= :limite
            """)
    List<Horario> paraLembrete(@Param("configuracaoAgendaId") Long configuracaoAgendaId,
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
            select a.agenda.configuracaoAgenda.nome, count(a) from Horario a
            where (:unidadeId is null or a.agenda.unidadeSaude.id = :unidadeId)
              and a.dataHora between :inicio and :fim
            group by a.agenda.configuracaoAgenda.id, a.agenda.configuracaoAgenda.nome
            order by count(a) desc, a.agenda.configuracaoAgenda.nome asc
            """)
    List<Object[]> topConfiguracaoAgendas(@Param("unidadeId") Long unidadeId,
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
