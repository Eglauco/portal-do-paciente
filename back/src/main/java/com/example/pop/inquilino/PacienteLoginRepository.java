package com.example.pop.inquilino;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteLoginRepository extends JpaRepository<PacienteLogin, Long> {

    Optional<PacienteLogin> findByCpf(String cpf);
}
