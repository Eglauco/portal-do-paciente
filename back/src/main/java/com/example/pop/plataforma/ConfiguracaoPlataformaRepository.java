package com.example.pop.plataforma;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso à {@link ConfiguracaoPlataforma} (tabela única no schema {@code public}). */
public interface ConfiguracaoPlataformaRepository extends JpaRepository<ConfiguracaoPlataforma, Long> {

    Optional<ConfiguracaoPlataforma> findByChave(String chave);
}
