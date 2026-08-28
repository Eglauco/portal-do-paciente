package com.example.pop.prontuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProntuarioRepository extends JpaRepository<Prontuario, Long> {

    boolean existsByNumeroAtendimento(String numeroAtendimento);

    boolean existsByNumeroAtendimentoAndIdNot(String numeroAtendimento, Long id);

    @Query("""
            select p from Prontuario p
            where (:numero = '' or lower(p.numeroAtendimento) like lower(concat('%', :numero, '%')))
              and (:pacienteId is null or p.agendamento.paciente.id = :pacienteId)
              and (:unidadeId is null or p.agendamento.unidadeSaude.id = :unidadeId)
            """)
    Page<Prontuario> search(
            @Param("numero") String numero,
            @Param("pacienteId") Long pacienteId,
            @Param("unidadeId") Long unidadeId,
            Pageable pageable);
}
