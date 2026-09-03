package com.example.pop.lembrete;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LembreteRepository extends JpaRepository<Lembrete, Long> {

    /** Lembretes de um procedimento (para o CRUD do admin). */
    List<Lembrete> findByProcedimentoIdOrderByHorasAntecedenciaDesc(Long procedimentoId);
}
