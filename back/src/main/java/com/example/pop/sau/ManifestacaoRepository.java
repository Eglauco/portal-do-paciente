package com.example.pop.sau;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManifestacaoRepository extends JpaRepository<Manifestacao, Long> {

    /** Carrega a manifestação garantindo que é do paciente informado (escopo do app). */
    Optional<Manifestacao> findByIdAndPacienteId(Long id, Long pacienteId);

    /** Manifestações do paciente, mais recentes (atualizadas) primeiro. */
    Page<Manifestacao> findByPacienteIdOrderByAtualizadoEmDesc(Long pacienteId, Pageable pageable);

    /** Existe alguma manifestação usando este tipo? (trava a exclusão do tipo). */
    boolean existsByTipoId(Long tipoId);

    /** Busca do back-office (SAU): por unidade, tipo e status (todos opcionais). */
    @Query("""
            select m from Manifestacao m
            where (:unidadeId is null or m.unidadeSaude.id = :unidadeId)
              and (:tipoId is null or m.tipo.id = :tipoId)
              and (:status is null or m.status = :status)
            """)
    Page<Manifestacao> search(
            @Param("unidadeId") Long unidadeId,
            @Param("tipoId") Long tipoId,
            @Param("status") StatusManifestacao status,
            Pageable pageable);

    // ===================== Dashboard (agregações) =====================

    @Query("""
            select count(m) from Manifestacao m
            where (:unidadeId is null or m.unidadeSaude.id = :unidadeId)
              and m.criadoEm between :inicio and :fim
            """)
    long contarCriadasPeriodo(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Série diária de manifestações criadas (data, quantidade). */
    @Query("""
            select cast(m.criadoEm as date), count(m) from Manifestacao m
            where (:unidadeId is null or m.unidadeSaude.id = :unidadeId)
              and m.criadoEm between :inicio and :fim
            group by cast(m.criadoEm as date)
            """)
    List<Object[]> serieCriacao(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Retrato ATUAL: todas as manifestações da unidade agrupadas por status. */
    @Query("""
            select m.status, count(m) from Manifestacao m
            where (:unidadeId is null or m.unidadeSaude.id = :unidadeId)
            group by m.status
            """)
    List<Object[]> agruparPorStatus(@Param("unidadeId") Long unidadeId);

    @Query("""
            select m.tipo.nome, count(m) from Manifestacao m
            where (:unidadeId is null or m.unidadeSaude.id = :unidadeId)
              and m.criadoEm between :inicio and :fim
            group by m.tipo.id, m.tipo.nome
            order by count(m) desc, m.tipo.nome asc
            """)
    List<Object[]> porTipo(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
