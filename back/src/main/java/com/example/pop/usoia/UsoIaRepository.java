package com.example.pop.usoia;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsoIaRepository extends JpaRepository<UsoIa, Long> {

    // Filtros nuláveis via coalesce(:param, coluna): quando o parâmetro é null a condição vira
    // "coluna = coluna" (sempre true) E o coalesce dá o TIPO ao parâmetro — evita o erro do Postgres
    // "não foi possível determinar o tipo de dados" que ocorre com (:param is null or ...).

    // A tela e sempre escopada por 1 UNIDADE (a ativa do admin): u.unidade_id = :unidadeId e um filtro
    // FIXO (nao nulavel). Registros antigos sem unidade (unidade_id NULL) ficam de fora — "daqui pra frente".

    /** Busca da unidade, com filtros de tipo, período (de/até) e texto. Sem ORDER BY (vem do Pageable). */
    @Query("""
            select u from UsoIa u
            where u.unidadeId = :unidadeId
              and u.tipo = coalesce(:tipo, u.tipo)
              and u.criadoEm >= coalesce(:de, u.criadoEm)
              and u.criadoEm <= coalesce(:ate, u.criadoEm)
              and (:busca = '' or lower(u.descricao) like lower(concat('%', :busca, '%')))
            """)
    Page<UsoIa> search(@Param("unidadeId") Long unidadeId, @Param("tipo") UsoIaTipo tipo,
            @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate, @Param("busca") String busca,
            Pageable pageable);

    /** Soma o consumo (custo/tokens) e conta os registros da unidade, para os MESMOS filtros da busca. */
    @Query("""
            select new com.example.pop.usoia.UsoIaTotais(
                sum(u.custoUsd), sum(u.tokensEntrada), sum(u.tokensSaida), count(u))
            from UsoIa u
            where u.unidadeId = :unidadeId
              and u.tipo = coalesce(:tipo, u.tipo)
              and u.criadoEm >= coalesce(:de, u.criadoEm)
              and u.criadoEm <= coalesce(:ate, u.criadoEm)
              and (:busca = '' or lower(u.descricao) like lower(concat('%', :busca, '%')))
            """)
    UsoIaTotais somar(@Param("unidadeId") Long unidadeId, @Param("tipo") UsoIaTipo tipo,
            @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate, @Param("busca") String busca);
}
