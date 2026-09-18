package com.example.pop.unidade;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadeFaqRepository extends JpaRepository<UnidadeFaq, Long> {

    /** A unidade tem ao menos um item de FAQ? (gate da IA: sem FAQ, o chat vai direto ao humano). */
    boolean existsByUnidadeId(Long unidadeId);

    /** FAQ da unidade na ordem de exibição/uso. */
    List<UnidadeFaq> findByUnidadeIdOrderByOrdemAscIdAsc(Long unidadeId);
}
