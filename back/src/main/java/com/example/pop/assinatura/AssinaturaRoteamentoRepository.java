package com.example.pop.assinatura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssinaturaRoteamentoRepository extends JpaRepository<AssinaturaRoteamento, Long> {

    Optional<AssinaturaRoteamento> findByChave(String chave);
}
