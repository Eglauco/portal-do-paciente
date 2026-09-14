package com.example.pop.conselho;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConselhoRepository extends JpaRepository<Conselho, Long> {

    @Query(value = """
            select c from Conselho c
            where (:id is null or c.id = :id)
              and lower(c.nome) like lower(concat('%', :nome, '%'))
            """,
            countQuery = """
            select count(c) from Conselho c
            where (:id is null or c.id = :id)
              and lower(c.nome) like lower(concat('%', :nome, '%'))
            """)
    Page<Conselho> search(@Param("id") Long id, @Param("nome") String nome, Pageable pageable);
}
