package com.example.pop.agendamento;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgendamentoLogRepository extends JpaRepository<AgendamentoLog, Long> {

    /** Linha do tempo do agendamento (mais antigo primeiro); join fetch nos atores p/ evitar N+1. */
    @Query("""
            select l from AgendamentoLog l
            left join fetch l.paciente
            left join fetch l.responsavel
            left join fetch l.usuario
            where l.agendamento.id = :agendamentoId
            order by l.criadoEm asc, l.id asc
            """)
    List<AgendamentoLog> findByAgendamentoIdOrderByCriadoEmAsc(@Param("agendamentoId") Long agendamentoId);

    /** O responsável fez alguma troca de status (lançamento no log)? Trava a remoção do responsável. */
    boolean existsByResponsavel_Id(Long responsavelId);
}
