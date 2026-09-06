package com.example.pop.push;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    boolean existsByToken(String token);

    List<Dispositivo> findByToken(String token);

    Optional<Dispositivo> findFirstByToken(String token);

    /**
     * Aparelhos legados de um paciente AINDA não migrados para o modelo por conta
     * (conta_id nulo). Escopar por conta_id nulo evita colidir com aparelhos de
     * outras contas que por acaso tenham o mesmo paciente_id.
     */
    List<Dispositivo> findByPacienteIdAndContaIdIsNull(Long pacienteId);

    /** Aparelhos de uma conta (para desvincular no logout). */
    List<Dispositivo> findByContaId(Long contaId);

    /** Aparelhos de um conjunto de contas (fan-out do push por conta). */
    List<Dispositivo> findByContaIdIn(Collection<Long> contaIds);
}
