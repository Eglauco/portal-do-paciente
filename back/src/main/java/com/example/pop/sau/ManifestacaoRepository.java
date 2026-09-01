package com.example.pop.sau;

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
}
