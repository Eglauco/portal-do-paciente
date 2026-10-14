package com.example.pop.inquilino;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Credencial central do paciente (public.paciente_identidade), chaveada por CPF. */
public interface PacienteIdentidadeRepository extends JpaRepository<PacienteIdentidade, Long> {

    Optional<PacienteIdentidade> findByCpf(String cpf);

    /**
     * Leitura com LOCK para o login por senha: {@code SELECT ... FOR UPDATE} serializa as tentativas
     * concorrentes do MESMO CPF, para o read-modify-write do contador de tentativas/lockout ser atômico
     * (sem lost-update). Só vale dentro de uma transação. CPF inexistente → sem linha, sem lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PacienteIdentidade i where i.cpf = :cpf")
    Optional<PacienteIdentidade> buscarParaAtualizarPorCpf(@Param("cpf") String cpf);
}
