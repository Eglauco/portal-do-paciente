package com.example.pop.profissional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfissionalSaudeRepository extends JpaRepository<ProfissionalSaude, Long> {

    @Query(value = """
            select u from ProfissionalSaude u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
            """,
            countQuery = """
            select count(u) from ProfissionalSaude u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
            """)
    Page<ProfissionalSaude> search(@Param("id") Long id, @Param("nome") String nome, Pageable pageable);
}
