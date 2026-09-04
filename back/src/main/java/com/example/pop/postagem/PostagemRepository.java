package com.example.pop.postagem;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PostagemRepository extends JpaRepository<Postagem, Long> {

    /**
     * Marca os comentários como vistos com um UPDATE pontual (só a coluna), evitando
     * sobrescrever ultimo_comentario_paciente_em numa corrida com um novo comentário.
     * Grava o "high-water mark" (o último comentário que o admin viu).
     */
    @Modifying
    @Transactional
    @Query("update Postagem p set p.comentariosVistosEm = :em where p.id = :id")
    void marcarComentariosVistos(@Param("id") Long id, @Param("em") LocalDateTime em);

    @Query("""
            select p from Postagem p
            where (:titulo = '' or lower(p.titulo) like lower(concat('%', :titulo, '%')))
              and (:unidadeId is null or p.unidadeSaude.id = :unidadeId)
              and (:comentarios is null or p.habilitarComentarios = :comentarios)
              and (:novoComentario is null
                   or (:novoComentario = true and p.ultimoComentarioPacienteEm is not null
                        and (p.comentariosVistosEm is null or p.ultimoComentarioPacienteEm > p.comentariosVistosEm))
                   or (:novoComentario = false and (p.ultimoComentarioPacienteEm is null
                        or (p.comentariosVistosEm is not null and p.ultimoComentarioPacienteEm <= p.comentariosVistosEm))))
            """)
    Page<Postagem> search(
            @Param("titulo") String titulo,
            @Param("unidadeId") Long unidadeId,
            @Param("comentarios") Boolean comentarios,
            @Param("novoComentario") Boolean novoComentario,
            Pageable pageable);
}
