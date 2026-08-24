package com.example.pop.procedimento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedimentoRepository extends JpaRepository<Procedimento, Long> {

    @Query(value = """
            select u from Procedimento u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
            """,
            countQuery = """
            select count(u) from Procedimento u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
            """)
    Page<Procedimento> search(@Param("id") Long id, @Param("nome") String nome, Pageable pageable);
}
