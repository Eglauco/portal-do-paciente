package com.example.pop.prontuario;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.pop.assinatura.ProvedorAssinatura;

public interface TermoAssinaturaRepository extends JpaRepository<TermoAssinatura, Long> {

    /** Termos a assinar de um prontuário (mais antigos primeiro), para exibir no app. */
    List<TermoAssinatura> findByProntuario_IdOrderByCriadoEmAsc(Long prontuarioId);

    /** Idempotência: já existe pendência para este termo-de-procedimento neste prontuário? */
    boolean existsByProntuario_IdAndTermoProcedimento_Id(Long prontuarioId, Long termoProcedimentoId);

    /** Primeiro termo por chave do signatário do PACIENTE (Clicksign: pré-preencher os dados federais no widget). */
    Optional<TermoAssinatura> findFirstByProviderSignerId(String providerSignerId);

    /** Primeiro termo por chave do signatário do PROFISSIONAL (coassinatura). */
    Optional<TermoAssinatura> findFirstByProviderSignerIdProfissional(String providerSignerIdProfissional);

    /** Pendência do paciente logado (posse via prontuario.horario.paciente). */
    Optional<TermoAssinatura> findByIdAndProntuario_Horario_Paciente_Id(Long id, Long pacienteId);

    /**
     * Termos ligados a um documento do provedor. Normalmente 1, mas no modo COMBINADO (Autentique) vários
     * termos compartilham o MESMO documento — por isso lista (um {@code findBy...} Optional estouraria).
     */
    List<TermoAssinatura> findAllByProviderDocToken(String providerDocToken);

    /** Termos de um status num prontuário do paciente logado (posse), ordenados — base do lote. */
    List<TermoAssinatura> findByProntuario_IdAndProntuario_Horario_Paciente_IdAndStatusOrderByCriadoEmAsc(
            Long prontuarioId, Long pacienteId, StatusTermoAssinatura status);

    /** Termos de um prontuário do paciente logado (posse), ordenados — usado pelo endpoint leve de conferência. */
    List<TermoAssinatura> findByProntuario_IdAndProntuario_Horario_Paciente_IdOrderByCriadoEmAsc(
            Long prontuarioId, Long pacienteId);

    /** Termos em quaisquer dos status (posse), ordenados — base do lote (assinaveis = PENDENTE + TENTAR_NOVAMENTE). */
    List<TermoAssinatura> findByProntuario_IdAndProntuario_Horario_Paciente_IdAndStatusInOrderByCriadoEmAsc(
            Long prontuarioId, Long pacienteId, Collection<StatusTermoAssinatura> status);

    // ---------- Coassinatura: termos do PROFISSIONAL logado (posse via agendamento.profissionalSaude) ----------

    /** Termos num status do profissional (a fila "meus termos para assinar" = AGUARDANDO_PROFISSIONAL). */
    List<TermoAssinatura> findByStatusAndProntuario_Horario_Agenda_ProfissionalSaude_IdOrderByCriadoEmAsc(
            StatusTermoAssinatura status, Long profissionalSaudeId);

    /** DEV/inspeção: todos os termos num status (usado pelo inspetor dev da coassinatura). */
    List<TermoAssinatura> findByStatus(StatusTermoAssinatura status);

    /** DEV/inspeção: termos mais recentes de um provedor (usado pelo inspetor dev do Clicksign). */
    List<TermoAssinatura> findTop10ByProvedorOrderByIdDesc(ProvedorAssinatura provedor);

    /** Termos de um prontuário do profissional num status (conferência da coassinatura). */
    List<TermoAssinatura> findByProntuario_IdAndProntuario_Horario_Agenda_ProfissionalSaude_IdAndStatusOrderByCriadoEmAsc(
            Long prontuarioId, Long profissionalSaudeId, StatusTermoAssinatura status);

    /** Termos de um prontuário do profissional (posse), qualquer status — relê após conferir. */
    List<TermoAssinatura> findByProntuario_IdAndProntuario_Horario_Agenda_ProfissionalSaude_IdOrderByCriadoEmAsc(
            Long prontuarioId, Long profissionalSaudeId);
}
