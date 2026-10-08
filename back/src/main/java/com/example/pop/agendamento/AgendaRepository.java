package com.example.pop.agendamento;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Agendas (slots/sessões do profissional). Os pacientes marcados ficam em {@link Horario}. */
public interface AgendaRepository extends JpaRepository<Agenda, Long> {

    /**
     * Dedup/agrupamento da importação SIRESP: a agenda já criada para este código de integração DENTRO do tipo
     * (consulta/exame). O tipo é obrigatório na chave porque os id-spaces do CROSS podem colidir numericamente.
     */
    Optional<Agenda> findFirstByCodigoIntegracaoAndTipoAtendimento(String codigoIntegracao,
            TipoAtendimento tipoAtendimento);

    /**
     * Busca de agendas da tela: por unidade (escopo), dia e nome de profissional/especialidade (parcial).
     * Params nuláveis = sem filtro. Ordenação vem do Pageable.
     */
    @Query(value = """
            select a from Agenda a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.data = coalesce(:data, a.data)
              and (:profissionalNome is null or lower(a.profissionalSaude.nome) like :profissionalNome)
              and (:especialidadeNome is null or lower(a.especialidade.nome) like :especialidadeNome)
            """,
            countQuery = """
            select count(a) from Agenda a
            where (:unidadeId is null or a.unidadeSaude.id = :unidadeId)
              and a.data = coalesce(:data, a.data)
              and (:profissionalNome is null or lower(a.profissionalSaude.nome) like :profissionalNome)
              and (:especialidadeNome is null or lower(a.especialidade.nome) like :especialidadeNome)
            """)
    Page<Agenda> search(@Param("unidadeId") Long unidadeId,
            @Param("data") LocalDate data,
            @Param("profissionalNome") String profissionalNome,
            @Param("especialidadeNome") String especialidadeNome,
            Pageable pageable);
}
