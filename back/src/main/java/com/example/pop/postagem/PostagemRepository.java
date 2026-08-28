package com.example.pop.postagem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostagemRepository extends JpaRepository<Postagem, Long> {

    @Query("""
            select p from Postagem p
            where (:titulo = '' or lower(p.titulo) like lower(concat('%', :titulo, '%')))
              and (:unidadeId is null or p.unidadeSaude.id = :unidadeId)
              and (:comentarios is null or p.habilitarComentarios = :comentarios)
            """)
    Page<Postagem> search(
            @Param("titulo") String titulo,
            @Param("unidadeId") Long unidadeId,
            @Param("comentarios") Boolean comentarios,
            Pageable pageable);
}
