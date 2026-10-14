package com.example.pop.inquilino;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteLoginRepository extends JpaRepository<PacienteLogin, Long> {

    /** Vínculos (inquilinos) de um CPF. Fase B: um CPF pode estar em N clínicas → List. */
    List<PacienteLogin> findByCpf(String cpf);

    /** Vínculo específico (cpf, inquilino) — para a idempotência do roteamento por inquilino. */
    Optional<PacienteLogin> findByCpfAndInquilinoId(String cpf, Long inquilinoId);

    /** 1º vínculo do CPF em ordem DETERMINÍSTICA — fallback legado de roteamento (sem X-Inquilino). */
    Optional<PacienteLogin> findFirstByCpfOrderByIdAsc(String cpf);

    /**
     * Garante o vínculo (cpf, inquilino) de forma ATÔMICA e idempotente, à prova de corrida: o
     * {@code ON CONFLICT DO NOTHING} deixa o banco resolver o duplo-insert concorrente (índice único
     * uk_paciente_login_cpf_inq, V158) sem lançar exceção — ao contrário do find-then-save, que sob
     * concorrência estoura DataIntegrityViolationException no commit e derruba a transação do cadastro.
     * {@code criado_em} é NOT NULL sem default no banco, então preenche aqui com now().
     */
    @Modifying
    @Query(value = "INSERT INTO public.paciente_login (cpf, inquilino_id, criado_em) "
            + "VALUES (:cpf, :inquilinoId, now()) ON CONFLICT (cpf, inquilino_id) DO NOTHING", nativeQuery = true)
    void inserirSeAusente(@Param("cpf") String cpf, @Param("inquilinoId") Long inquilinoId);
}
