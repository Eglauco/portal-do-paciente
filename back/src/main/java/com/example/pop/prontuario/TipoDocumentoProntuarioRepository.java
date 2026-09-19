package com.example.pop.prontuario;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TipoDocumentoProntuarioRepository extends JpaRepository<TipoDocumentoProntuario, Long> {

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);

    @Query("""
            select t from TipoDocumentoProntuario t
            where (:nome = '' or lower(t.nome) like lower(concat('%', :nome, '%')))
              and (:ativo is null or t.ativo = :ativo)
            """)
    Page<TipoDocumentoProntuario> search(@Param("nome") String nome, @Param("ativo") Boolean ativo, Pageable pageable);

    /** Tipos ativos (para o seletor no upload do documento). */
    List<TipoDocumentoProntuario> findByAtivoTrueOrderByNomeAsc();
}
