package com.example.pop.configuracaoagenda;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TermoConfiguracaoAgendaRepository extends JpaRepository<TermoConfiguracaoAgenda, Long> {

    /** Documentos TCLE de um configuracaoAgenda (mais recentes primeiro), para o CRUD do admin. */
    List<TermoConfiguracaoAgenda> findByConfiguracaoAgendaIdOrderByCriadoEmDesc(Long configuracaoAgendaId);
}
