package com.example.pop.exame;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExameRepository extends JpaRepository<Exame, Long> {

    @Query(value = """
            select u from Exame u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
            """,
            countQuery = """
            select count(u) from Exame u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
            """)
    Page<Exame> search(@Param("id") Long id, @Param("nome") String nome, Pageable pageable);

    /** Unicidade do código de integração ignorando o próprio registro (edição passa o id; criação, -1). */
    boolean existsByCodigoIntegracaoAndIdNot(String codigoIntegracao, Long id);

    /** Exame pelo código de integração (SIRESP/CROSS) — único quando preenchido. */
    java.util.Optional<Exame> findByCodigoIntegracao(String codigoIntegracao);
}
