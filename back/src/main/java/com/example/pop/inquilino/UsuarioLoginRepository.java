package com.example.pop.inquilino;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioLoginRepository extends JpaRepository<UsuarioLogin, Long> {

    Optional<UsuarioLogin> findByEmailIgnoreCase(String email);
}
