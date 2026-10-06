package com.example.pop.configuracao;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfiguracaoRepository extends JpaRepository<Configuracao, Long> {

    /**
     * Busca do CRUD genérico: por nome OU chave (texto) e por tipo (ambos opcionais). EXCLUI os SEGREDO —
     * credenciais (tokens dos provedores) só aparecem/editam na tela dedicada de Provedores de assinatura.
     */
    @Query("""
            select c from Configuracao c
            where (lower(c.nome) like lower(concat('%', :busca, '%'))
                   or lower(c.chave) like lower(concat('%', :busca, '%')))
              and (:tipo is null or c.tipoConfiguracao = :tipo)
              and c.tipoConfiguracao <> com.example.pop.configuracao.TipoConfiguracao.SEGREDO
            """)
    Page<Configuracao> search(@Param("busca") String busca, @Param("tipo") TipoConfiguracao tipo, Pageable pageable);

    /** Acesso da regra de negócio pela chave única. */
    Optional<Configuracao> findByChave(String chave);
}
