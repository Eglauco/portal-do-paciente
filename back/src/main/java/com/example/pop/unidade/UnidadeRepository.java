package com.example.pop.unidade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnidadeRepository extends JpaRepository<Unidade, Long> {

    @Query(value = """
            select u from Unidade u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
            """,
            countQuery = """
            select count(u) from Unidade u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
            """)
    Page<Unidade> search(@Param("id") Long id, @Param("nome") String nome, Pageable pageable);

    // Unicidade do código de integração (único quando preenchido).
    boolean existsByCodigoIntegracao(String codigoIntegracao);

    boolean existsByCodigoIntegracaoAndIdNot(String codigoIntegracao, Long id);

    /** Unidade pelo código de integração (SIRESP/CROSS COD_UNIDADE_EXECUTANTE) — único quando preenchido. */
    java.util.Optional<Unidade> findByCodigoIntegracao(String codigoIntegracao);
}
