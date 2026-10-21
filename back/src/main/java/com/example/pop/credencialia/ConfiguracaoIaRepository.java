package com.example.pop.credencialia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoIaRepository extends JpaRepository<ConfiguracaoIa, Long> {

    /** Credencial de uma unidade específica. */
    Optional<ConfiguracaoIa> findByUnidadeId(Long unidadeId);

    /** Credencial PADRÃO do inquilino (linha com unidade_id NULL). */
    Optional<ConfiguracaoIa> findByUnidadeIdIsNull();
}
