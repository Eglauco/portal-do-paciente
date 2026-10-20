package com.example.pop.storage;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoS3Repository extends JpaRepository<ConfiguracaoS3, Long> {

    /** Config de uma unidade específica. */
    Optional<ConfiguracaoS3> findByUnidadeId(Long unidadeId);

    /** Config PADRÃO do inquilino (linha com unidade_id NULL). */
    Optional<ConfiguracaoS3> findByUnidadeIdIsNull();
}
