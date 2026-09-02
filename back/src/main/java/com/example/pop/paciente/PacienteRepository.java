package com.example.pop.paciente;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    Optional<Paciente> findByTelefone(String telefone);

    boolean existsByTelefone(String telefone);

    @Query(value = """
            select p from Paciente p
            where (:id is null or p.id = :id)
              and lower(p.nome) like lower(concat('%', :nome, '%'))
            """,
            countQuery = """
            select count(p) from Paciente p
            where (:id is null or p.id = :id)
              and lower(p.nome) like lower(concat('%', :nome, '%'))
            """)
    Page<Paciente> search(@Param("id") Long id, @Param("nome") String nome, Pageable pageable);

    /** Pacientes liberados a usar o app (dashboard). */
    long countByAtivoTrue();

    /** Pacientes com sessão de app amarrada a um aparelho (= usando o app de fato). */
    long countByDispositivoAtivoIsNotNull();
}
