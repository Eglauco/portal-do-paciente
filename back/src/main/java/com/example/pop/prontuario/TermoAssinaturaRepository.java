package com.example.pop.prontuario;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TermoAssinaturaRepository extends JpaRepository<TermoAssinatura, Long> {

    /** Termos a assinar de um prontuário (mais antigos primeiro), para exibir no app. */
    List<TermoAssinatura> findByProntuario_IdOrderByCriadoEmAsc(Long prontuarioId);

    /** Idempotência: já existe pendência para este termo-de-procedimento neste prontuário? */
    boolean existsByProntuario_IdAndTermoProcedimento_Id(Long prontuarioId, Long termoProcedimentoId);

    /** Pendência do paciente logado (posse via prontuario.agendamento.paciente). */
    Optional<TermoAssinatura> findByIdAndProntuario_Agendamento_Paciente_Id(Long id, Long pacienteId);

    /** Localiza a pendência pelo documento criado na ZapSign (usado pelo webhook doc_signed). */
    Optional<TermoAssinatura> findByZapsignDocToken(String zapsignDocToken);

    /** Termos de um status num prontuário do paciente logado (posse), ordenados — base do lote. */
    List<TermoAssinatura> findByProntuario_IdAndProntuario_Agendamento_Paciente_IdAndStatusOrderByCriadoEmAsc(
            Long prontuarioId, Long pacienteId, StatusTermoAssinatura status);

    /** Termos de um prontuário do paciente logado (posse), ordenados — usado pelo endpoint leve de conferência. */
    List<TermoAssinatura> findByProntuario_IdAndProntuario_Agendamento_Paciente_IdOrderByCriadoEmAsc(
            Long prontuarioId, Long pacienteId);

    /** Termos em quaisquer dos status (posse), ordenados — base do lote (assinaveis = PENDENTE + TENTAR_NOVAMENTE). */
    List<TermoAssinatura> findByProntuario_IdAndProntuario_Agendamento_Paciente_IdAndStatusInOrderByCriadoEmAsc(
            Long prontuarioId, Long pacienteId, Collection<StatusTermoAssinatura> status);
}
