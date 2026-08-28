package com.example.pop.usuario;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    @Query(value = """
            select u from Usuario u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
              and lower(u.email) like lower(concat('%', :email, '%'))
            """,
            countQuery = """
            select count(u) from Usuario u
            where (:id is null or u.id = :id)
              and lower(u.nome) like lower(concat('%', :nome, '%'))
              and lower(u.email) like lower(concat('%', :email, '%'))
            """)
    Page<Usuario> search(@Param("id") Long id, @Param("nome") String nome, @Param("email") String email,
            Pageable pageable);
}
