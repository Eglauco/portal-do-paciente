package com.example.pop.push;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    boolean existsByToken(String token);

    List<Dispositivo> findByToken(String token);

    Optional<Dispositivo> findFirstByToken(String token);

    /** Aparelhos do paciente (para push direcionado). */
    List<Dispositivo> findByPacienteId(Long pacienteId);
}
