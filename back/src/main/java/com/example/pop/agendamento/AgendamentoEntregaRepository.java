package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgendamentoEntregaRepository extends JpaRepository<AgendamentoEntrega, Long> {

    /** Destinatários (e seu estado de entrega) de um agendamento, na ordem de gravação. */
    List<AgendamentoEntrega> findByAgendamento_IdOrderByIdAsc(Long agendamentoId);

    /**
     * Entregas ainda "enviadas" com receipts a confirmar e disparadas até {@code criadoEmAte}
     * (receipts da Expo só ficam prontos ~15 min depois) — alvo do job de confirmação de entrega.
     */
    List<AgendamentoEntrega> findByEstadoAndReceiptsPendentesIsNotNullAndCriadoEmLessThanEqual(
            EstadoEntrega estado, LocalDateTime criadoEmAte);
}
