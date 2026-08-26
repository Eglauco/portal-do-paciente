package com.example.pop.push;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    boolean existsByToken(String token);

    List<Dispositivo> findByToken(String token);
}
