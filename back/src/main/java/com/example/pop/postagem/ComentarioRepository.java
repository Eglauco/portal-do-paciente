package com.example.pop.postagem;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComentarioRepository extends JpaRepository<Comentario, Long> {

    /** Comentários-raiz (sem pai) de uma postagem, paginados. Uso do admin (vê todos). */
    Page<Comentario> findByPostagemIdAndComentarioPaiIsNullOrderByCriadoEmDesc(Long postagemId, Pageable pageable);

    /**
     * Carrega um comentário já com o pai inicializado (join fetch), para navegar até a raiz
     * FORA de uma transação — assim a moderação por IA (chamada HTTP) pode rodar sem segurar
     * uma conexão do pool aberta.
     */
    @Query("select c from Comentario c left join fetch c.comentarioPai where c.id = :id")
    Optional<Comentario> findByIdComPai(@Param("id") Long id);

    /**
     * Comentários-raiz VISÍVEIS ao público/paciente: os PUBLICADO, mais os PENDENTE
     * ("em análise") do próprio paciente que está lendo. Filtra no banco para a paginação
     * (total/páginas) não vazar a existência de comentários ocultos. Quando {@code pacienteAtual}
     * é nulo (leitor anônimo), a comparação com pacienteId nunca casa → só os PUBLICADO.
     */
    @Query("""
            select c from Comentario c
            where c.postagem.id = :postagemId and c.comentarioPai is null
              and (c.statusModeracao = com.example.pop.postagem.StatusModeracao.PUBLICADO
                   or (c.statusModeracao = com.example.pop.postagem.StatusModeracao.PENDENTE
                       and c.pacienteId = :pacienteAtual))
            order by c.criadoEm desc
            """)
    Page<Comentario> findRaizesVisiveis(@Param("postagemId") Long postagemId,
            @Param("pacienteAtual") Long pacienteAtual, Pageable pageable);

    /** Respostas de um conjunto de comentários-raiz, em ordem cronológica. */
    List<Comentario> findByComentarioPaiIdInOrderByCriadoEmAsc(List<Long> comentarioPaiIds);

    /** Respostas de um único comentário-raiz, em ordem cronológica. */
    List<Comentario> findByComentarioPaiIdOrderByCriadoEmAsc(Long comentarioPaiId);

    /** Total de comentários da postagem (raízes + respostas). Uso do admin (conta todos). */
    long countByPostagemId(Long postagemId);

    /**
     * Total de comentários (raízes + respostas) VISÍVEIS ao público/paciente: os PUBLICADO,
     * mais os PENDENTE do próprio paciente. Evita que o contador do card do feed revele a
     * existência de comentários ocultos (pendentes de outros / rejeitados). Leitor anônimo
     * ({@code pacienteAtual} nulo) conta só os PUBLICADO.
     */
    @Query("""
            select count(c) from Comentario c
            where c.postagem.id = :postagemId
              and (c.statusModeracao = com.example.pop.postagem.StatusModeracao.PUBLICADO
                   or (c.statusModeracao = com.example.pop.postagem.StatusModeracao.PENDENTE
                       and c.pacienteId = :pacienteAtual))
            """)
    long countVisiveis(@Param("postagemId") Long postagemId, @Param("pacienteAtual") Long pacienteAtual);

    /** O responsável tem algum comentário (lançamento no feed)? Trava a remoção do responsável. */
    boolean existsByResponsavelId(Long responsavelId);
}
