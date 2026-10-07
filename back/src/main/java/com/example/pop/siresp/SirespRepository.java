package com.example.pop.siresp;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SirespRepository extends JpaRepository<Siresp, Long> {

    /** Dedup de agendamento (consulta): primeiro registro do mesmo horário (ID_AGE_CONSULTA_HOR) que já gerou um. */
    Optional<Siresp> findFirstByIdAgeConsultaHorAndAgendamentoIdIsNotNull(String idAgeConsultaHor);

    /** Dedup de agendamento (exame): primeiro registro do mesmo horário (ID_AGE_EXAME_HOR) que já gerou um. */
    Optional<Siresp> findFirstByIdAgeExameHorAndAgendamentoIdIsNotNull(String idAgeExameHor);

    // Filtros nuláveis via coalesce(:param, coluna): quando o parâmetro é null a condição vira "coluna = coluna"
    // (sempre true) e o coalesce dá o TIPO ao parâmetro — evita o erro do Postgres de tipo indeterminado.

    /**
     * Busca por unidade (escopo da tela), texto (paciente/CPF/profissional/especialidade) e status derivado:
     * {@code agendado} null = todos; true = já com agendamento; false = precisa de revisão (sem agendamento).
     * Sem ORDER BY (vem do Pageable).
     */
    @Query("""
            select s from Siresp s
            where s.unidadeSaudeId = coalesce(:unidadeId, s.unidadeSaudeId)
              and (:busca = '' or lower(s.nomePaciente) like lower(concat('%', :busca, '%'))
                   or lower(s.cpf) like lower(concat('%', :busca, '%'))
                   or lower(s.nomeProfissional) like lower(concat('%', :busca, '%'))
                   or lower(s.nomeEspecialidade) like lower(concat('%', :busca, '%')))
              and (:agendado is null
                   or (:agendado = true and s.agendamentoId is not null)
                   or (:agendado = false and s.agendamentoId is null))
              and (:statusEnvio is null or s.statusEnvio = :statusEnvio)
              and (:tipoMovimento is null or s.tipoMovimento = :tipoMovimento)
            """)
    Page<Siresp> search(@Param("unidadeId") Long unidadeId, @Param("busca") String busca,
            @Param("agendado") Boolean agendado, @Param("statusEnvio") StatusEnvio statusEnvio,
            @Param("tipoMovimento") TipoMovimento tipoMovimento, Pageable pageable);
}
