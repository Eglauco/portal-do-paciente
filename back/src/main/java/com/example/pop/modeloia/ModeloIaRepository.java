package com.example.pop.modeloia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloIaRepository extends JpaRepository<ModeloIa, Long> {

    Optional<ModeloIa> findByModeloId(String modeloId);

    /** Modelos ativos (para os selects), em ordem de nome. */
    List<ModeloIa> findByAtivoTrueOrderByNomeAsc();

    boolean existsByModeloIdAndIdNot(String modeloId, Long id);
}
