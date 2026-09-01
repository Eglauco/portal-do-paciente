package com.example.pop.sau;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TipoManifestacaoRepository extends JpaRepository<TipoManifestacao, Long> {

    /** Tipos ativos, em ordem alfabética (app do paciente na hora de abrir). */
    List<TipoManifestacao> findByAtivoTrueOrderByNome();

    /** Busca do CRUD: por nome e situação (ambos opcionais). */
    @Query("""
            select t from TipoManifestacao t
            where lower(t.nome) like lower(concat('%', :nome, '%'))
              and (:ativo is null or t.ativo = :ativo)
            """)
    Page<TipoManifestacao> search(@Param("nome") String nome, @Param("ativo") Boolean ativo, Pageable pageable);
}
