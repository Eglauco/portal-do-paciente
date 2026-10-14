package com.example.pop.inquilino;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteLoginRepository extends JpaRepository<PacienteLogin, Long> {

    /** Vínculos (inquilinos) de um CPF. Fase B: um CPF pode estar em N clínicas → List. */
    List<PacienteLogin> findByCpf(String cpf);

    /** Vínculo específico (cpf, inquilino) — para a idempotência do roteamento por inquilino. */
    Optional<PacienteLogin> findByCpfAndInquilinoId(String cpf, Long inquilinoId);

    /** 1º vínculo do CPF em ordem DETERMINÍSTICA — fallback legado de roteamento (sem X-Inquilino). */
    Optional<PacienteLogin> findFirstByCpfOrderByIdAsc(String cpf);
}
