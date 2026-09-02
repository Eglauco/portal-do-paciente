package com.example.pop.sau;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManifestacaoMensagemRepository extends JpaRepository<ManifestacaoMensagem, Long> {

    /** Thread completa da manifestação, em ordem cronológica (join fetch no atendente p/ evitar N+1). */
    @Query("select m from ManifestacaoMensagem m left join fetch m.usuario "
            + "where m.manifestacao.id = :manifestacaoId order by m.criadoEm asc")
    List<ManifestacaoMensagem> findByManifestacaoIdOrderByCriadoEmAsc(@Param("manifestacaoId") Long manifestacaoId);

    /** Última mensagem (para o resumo na listagem). */
    ManifestacaoMensagem findFirstByManifestacaoIdOrderByCriadoEmDesc(Long manifestacaoId);

    // ===================== Dashboard (agregações) =====================

    /** Mensagens da thread no período agrupadas por autor (PACIENTE/SAU). */
    @Query("""
            select mm.autor, count(mm) from ManifestacaoMensagem mm
            where (:unidadeId is null or mm.manifestacao.unidadeSaude.id = :unidadeId)
              and mm.criadoEm between :inicio and :fim
            group by mm.autor
            """)
    List<Object[]> agruparPorAutorPeriodo(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Quem mais respondeu no SAU (por atendente) no período. */
    @Query("""
            select mm.usuario.nome, count(mm) from ManifestacaoMensagem mm
            where (:unidadeId is null or mm.manifestacao.unidadeSaude.id = :unidadeId)
              and mm.autor = com.example.pop.sau.AutorManifestacao.SAU
              and mm.usuario is not null
              and mm.criadoEm between :inicio and :fim
            group by mm.usuario.id, mm.usuario.nome
            order by count(mm) desc, mm.usuario.nome asc
            """)
    List<Object[]> cargaPorAtendente(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
