package com.example.pop.postagem;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComentarioRepository extends JpaRepository<Comentario, Long> {

    /** Comentários-raiz (sem pai) de uma postagem, paginados. */
    Page<Comentario> findByPostagemIdAndComentarioPaiIsNullOrderByCriadoEmDesc(Long postagemId, Pageable pageable);

    /** Respostas de um conjunto de comentários-raiz, em ordem cronológica. */
    List<Comentario> findByComentarioPaiIdInOrderByCriadoEmAsc(List<Long> comentarioPaiIds);

    /** Respostas de um único comentário-raiz, em ordem cronológica. */
    List<Comentario> findByComentarioPaiIdOrderByCriadoEmAsc(Long comentarioPaiId);

    /** Total de comentários da postagem (raízes + respostas). */
    long countByPostagemId(Long postagemId);
}
