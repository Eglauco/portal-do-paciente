package com.example.pop.postagem;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CurtidaRepository extends JpaRepository<Curtida, Long> {

    long countByPostagemId(Long postagemId);

    Optional<Curtida> findByPostagemIdAndDispositivoId(Long postagemId, String dispositivoId);

    boolean existsByPostagemIdAndDispositivoId(Long postagemId, String dispositivoId);
}
