package com.example.pop.inquilino;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InquilinoRepository extends JpaRepository<Inquilino, Long> {

    Optional<Inquilino> findBySchemaName(String schemaName);
}
