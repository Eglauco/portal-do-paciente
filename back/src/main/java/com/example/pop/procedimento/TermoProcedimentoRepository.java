package com.example.pop.procedimento;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TermoProcedimentoRepository extends JpaRepository<TermoProcedimento, Long> {

    /** Documentos TCLE de um procedimento (mais recentes primeiro), para o CRUD do admin. */
    List<TermoProcedimento> findByProcedimentoIdOrderByCriadoEmDesc(Long procedimentoId);
}
