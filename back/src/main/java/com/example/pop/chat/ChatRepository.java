package com.example.pop.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    /** Carrega a conversa garantindo que é do paciente informado (escopo do app). */
    Optional<Chat> findByIdAndPacienteId(Long id, Long pacienteId);

    /** Conversa única do par paciente+unidade (base do "abrir ou criar"). */
    Optional<Chat> findByPacienteIdAndUnidadeSaudeId(Long pacienteId, Long unidadeId);

    /** Id do dono da conversa (sem carregar o agregado) — usado na autorização do WebSocket. */
    @Query("select c.paciente.id from Chat c where c.id = :id")
    Optional<Long> findPacienteIdById(@Param("id") Long id);

    @Query(value = """
            select c from Chat c
            left join fetch c.responsavel
            where (:pacienteId is null or c.paciente.id = :pacienteId)
              and (:unidadeId is null or c.unidadeSaude.id = :unidadeId)
              and (:responsavelId is null or c.responsavel.id = :responsavelId)
              and (:status is null or c.status = :status)
              and (:excluirResolvidos = false or c.status <> :statusResolvido)
            """,
            countQuery = """
            select count(c) from Chat c
            where (:pacienteId is null or c.paciente.id = :pacienteId)
              and (:unidadeId is null or c.unidadeSaude.id = :unidadeId)
              and (:responsavelId is null or c.responsavel.id = :responsavelId)
              and (:status is null or c.status = :status)
              and (:excluirResolvidos = false or c.status <> :statusResolvido)
            """)
    Page<Chat> search(
            @Param("pacienteId") Long pacienteId,
            @Param("unidadeId") Long unidadeId,
            @Param("responsavelId") Long responsavelId,
            @Param("status") StatusChat status,
            @Param("excluirResolvidos") boolean excluirResolvidos,
            @Param("statusResolvido") StatusChat statusResolvido,
            Pageable pageable);

    // ===================== Dashboard (agregações) =====================

    @Query("""
            select count(c) from Chat c
            where (:unidadeId is null or c.unidadeSaude.id = :unidadeId)
              and c.criadoEm between :inicio and :fim
            """)
    long contarCriadosPeriodo(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Série diária de conversas criadas (data, quantidade). */
    @Query("""
            select cast(c.criadoEm as date), count(c) from Chat c
            where (:unidadeId is null or c.unidadeSaude.id = :unidadeId)
              and c.criadoEm between :inicio and :fim
            group by cast(c.criadoEm as date)
            """)
    List<Object[]> serieCriacao(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Retrato ATUAL: distribuição de todas as conversas da unidade por status. */
    @Query("""
            select c.status, count(c) from Chat c
            where (:unidadeId is null or c.unidadeSaude.id = :unidadeId)
            group by c.status
            """)
    List<Object[]> agruparPorStatus(@Param("unidadeId") Long unidadeId);

    /** Conversas ainda em aberto sem atendente (backlog a assumir). */
    @Query("""
            select count(c) from Chat c
            where (:unidadeId is null or c.unidadeSaude.id = :unidadeId)
              and c.responsavel is null
              and c.status <> com.example.pop.chat.StatusChat.RESOLVIDO
            """)
    long contarSemResponsavel(@Param("unidadeId") Long unidadeId);

    /** Carga por atendente: conversas em aberto de cada responsável. */
    @Query("""
            select c.responsavel.nome, count(c) from Chat c
            where (:unidadeId is null or c.unidadeSaude.id = :unidadeId)
              and c.responsavel is not null
              and c.status <> com.example.pop.chat.StatusChat.RESOLVIDO
            group by c.responsavel.id, c.responsavel.nome
            order by count(c) desc, c.responsavel.nome asc
            """)
    List<Object[]> cargaPorAtendente(@Param("unidadeId") Long unidadeId);
}
