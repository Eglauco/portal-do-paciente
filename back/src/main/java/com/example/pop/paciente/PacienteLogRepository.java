package com.example.pop.paciente;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteLogRepository extends JpaRepository<PacienteLog, Long> {

    /** Linha do tempo de auditoria do paciente (mais antigo primeiro). */
    List<PacienteLog> findByPacienteIdOrderByCriadoEmAscIdAsc(Long pacienteId);

    /** O responsável consta como ator de algum evento de auditoria? Trava a remoção do responsável. */
    boolean existsByResponsavel_Id(Long responsavelId);
}
