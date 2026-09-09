package com.example.pop.configuracao;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfiguracaoRepository extends JpaRepository<Configuracao, Long> {

    /** Busca do CRUD: por nome OU chave (texto) e por tipo (ambos opcionais). */
    @Query("""
            select c from Configuracao c
            where (lower(c.nome) like lower(concat('%', :busca, '%'))
                   or lower(c.chave) like lower(concat('%', :busca, '%')))
              and (:tipo is null or c.tipoConfiguracao = :tipo)
            """)
    Page<Configuracao> search(@Param("busca") String busca, @Param("tipo") TipoConfiguracao tipo, Pageable pageable);

    /** Acesso da regra de negócio pela chave única. */
    Optional<Configuracao> findByChave(String chave);
}
