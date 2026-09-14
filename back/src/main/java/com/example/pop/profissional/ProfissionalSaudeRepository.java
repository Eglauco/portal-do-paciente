package com.example.pop.profissional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfissionalSaudeRepository extends JpaRepository<ProfissionalSaude, Long> {

    // Unicidade quando preenchido (ignoram o próprio registro na edição, via id != :id).
    boolean existsByCpfAndIdNot(String cpf, Long id);

    boolean existsByCnsAndIdNot(String cns, Long id);

    boolean existsByCodigoIntegracaoAndIdNot(String codigoIntegracao, Long id);

    @Query(value = """
            select u from ProfissionalSaude u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
              and (:ativo is null or u.ativo = :ativo)
            """,
            countQuery = """
            select count(u) from ProfissionalSaude u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
              and (:ativo is null or u.ativo = :ativo)
            """)
    Page<ProfissionalSaude> search(@Param("id") Long id, @Param("nome") String nome,
            @Param("ativo") Boolean ativo, Pageable pageable);
}
