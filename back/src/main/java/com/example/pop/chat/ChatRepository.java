package com.example.pop.chat;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    /** Carrega a conversa garantindo que é do paciente informado (escopo do app). */
    Optional<Chat> findByIdAndPacienteId(Long id, Long pacienteId);

    @Query("""
            select c from Chat c
            where (:pacienteId is null or c.paciente.id = :pacienteId)
              and (:unidadeId is null or c.unidadeSaude.id = :unidadeId)
              and (:status is null or c.status = :status)
              and (:excluirResolvidos = false or c.status <> :statusResolvido)
            """)
    Page<Chat> search(
            @Param("pacienteId") Long pacienteId,
            @Param("unidadeId") Long unidadeId,
            @Param("status") StatusChat status,
            @Param("excluirResolvidos") boolean excluirResolvidos,
            @Param("statusResolvido") StatusChat statusResolvido,
            Pageable pageable);
}
