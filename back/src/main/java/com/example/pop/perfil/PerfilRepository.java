package com.example.pop.perfil;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerfilRepository extends JpaRepository<Perfil, Long> {

    Optional<Perfil> findByNomeIgnoreCase(String nome);

    /**
     * Busca paginada com filtros opcionais por código (id) e nome. As coleções
     * (telas/unidades) são EAGER e carregam por selects secundários — não entram
     * no join, então a paginação continua no nível do SQL.
     */
    @Query(value = """
            select p from Perfil p
            where (:codigo is null or p.id = :codigo)
              and lower(p.nome) like lower(concat('%', :nome, '%'))
            """,
            countQuery = """
            select count(p) from Perfil p
            where (:codigo is null or p.id = :codigo)
              and lower(p.nome) like lower(concat('%', :nome, '%'))
            """)
    Page<Perfil> search(@Param("codigo") Long codigo, @Param("nome") String nome, Pageable pageable);
}
