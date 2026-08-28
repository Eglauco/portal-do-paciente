package com.example.pop.categorianps;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaNpsRepository extends JpaRepository<CategoriaNps, Long> {

    @Query(value = """
            select c from CategoriaNps c
            where (:id is null or c.id = :id)
              and lower(c.nome) like lower(concat('%', :nome, '%'))
            """,
            countQuery = """
            select count(c) from CategoriaNps c
            where (:id is null or c.id = :id)
              and lower(c.nome) like lower(concat('%', :nome, '%'))
            """)
    Page<CategoriaNps> search(@Param("id") Long id, @Param("nome") String nome, Pageable pageable);

    /** Categorias ativas, em ordem alfabética (para o app exibir na avaliação). */
    List<CategoriaNps> findByAtivoTrueOrderByNome();
}
