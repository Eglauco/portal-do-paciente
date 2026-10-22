package com.example.pop.modeloia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.pop.credencialia.TipoProvedorIa;

public interface ModeloIaRepository extends JpaRepository<ModeloIa, Long> {

    Optional<ModeloIa> findByModeloId(String modeloId);

    /** Modelos ativos (para os selects), em ordem de nome. */
    List<ModeloIa> findByAtivoTrueOrderByNomeAsc();

    /** Modelos ativos de um PROVEDOR (o select da credencial filtra pelo provedor do escopo). */
    List<ModeloIa> findByAtivoTrueAndProvedorOrderByNomeAsc(TipoProvedorIa provedor);

    /** Provedores que têm ao menos um modelo ativo (alimenta o select de provedor da credencial). */
    @Query("select distinct m.provedor from ModeloIa m where m.ativo = true")
    List<TipoProvedorIa> provedoresComModeloAtivo();

    boolean existsByModeloIdAndIdNot(String modeloId, Long id);
}
