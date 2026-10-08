package com.example.pop.lembrete;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LembreteRepository extends JpaRepository<Lembrete, Long> {

    /** Lembretes de um configuracaoAgenda (para o CRUD do admin). */
    List<Lembrete> findByConfiguracaoAgendaIdOrderByHorasAntecedenciaDesc(Long configuracaoAgendaId);
}
