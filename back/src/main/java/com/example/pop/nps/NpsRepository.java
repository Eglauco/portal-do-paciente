package com.example.pop.nps;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpsRepository extends JpaRepository<Nps, Long> {

    boolean existsByAgendamentoId(Long agendamentoId);

    Optional<Nps> findByAgendamentoId(Long agendamentoId);

    /** Carrega o NPS garantindo que é do paciente (via agendamento.paciente). Escopo do app. */
    Optional<Nps> findByIdAndAgendamento_Paciente_Id(Long id, Long pacienteId);

    @Query("""
            select n from Nps n
            where (:status is null or n.status = :status)
              and (:pacienteId is null or n.agendamento.paciente.id = :pacienteId)
              and (:unidadeId is null or n.agendamento.unidadeSaude.id = :unidadeId)
            """)
    Page<Nps> search(
            @Param("status") StatusNps status,
            @Param("pacienteId") Long pacienteId,
            @Param("unidadeId") Long unidadeId,
            Pageable pageable);

    // ===================== Dashboard (agregações) =====================

    /** NPS GERADOS no período agrupados por status (via criadoEm). */
    @Query("""
            select n.status, count(n) from Nps n
            where (:unidadeId is null or n.agendamento.unidadeSaude.id = :unidadeId)
              and n.criadoEm between :inicio and :fim
            group by n.status
            """)
    List<Object[]> agruparPorStatus(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /**
     * Série diária de avaliações GERADAS por dia (data, quantidade). Ancorada em
     * criadoEm — o mesmo referencial de gerados/respondidos e da satisfação, para
     * que os números da tela se reconciliem (a taxa de resposta é do coorte gerado).
     */
    @Query("""
            select cast(n.criadoEm as date), count(n) from Nps n
            where (:unidadeId is null or n.agendamento.unidadeSaude.id = :unidadeId)
              and n.criadoEm between :inicio and :fim
            group by cast(n.criadoEm as date)
            """)
    List<Object[]> serieCriacao(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Média geral das avaliações respondidas do coorte gerado no período. */
    @Query("""
            select avg(n.media) from Nps n
            where (:unidadeId is null or n.agendamento.unidadeSaude.id = :unidadeId)
              and n.media is not null
              and n.criadoEm between :inicio and :fim
            """)
    Double mediaGeral(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Médias por avaliação (para classificar satisfeito/neutro/insatisfeito em Java). */
    @Query("""
            select n.media from Nps n
            where (:unidadeId is null or n.agendamento.unidadeSaude.id = :unidadeId)
              and n.media is not null
              and n.criadoEm between :inicio and :fim
            """)
    List<Double> mediasRespondidasPeriodo(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Média (1–5) por categoria de NPS no período (coorte gerado). */
    @Query("""
            select c.categoria.nome, avg(c.nota), count(c) from NpsCategoriaNota c
            where (:unidadeId is null or c.nps.agendamento.unidadeSaude.id = :unidadeId)
              and c.nps.criadoEm between :inicio and :fim
            group by c.categoria.id, c.categoria.nome
            order by avg(c.nota) desc, c.categoria.nome asc
            """)
    List<Object[]> mediaPorCategoria(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Distribuição das notas individuais (1..5 estrelas) no período (coorte gerado). */
    @Query("""
            select c.nota, count(c) from NpsCategoriaNota c
            where (:unidadeId is null or c.nps.agendamento.unidadeSaude.id = :unidadeId)
              and c.nps.criadoEm between :inicio and :fim
            group by c.nota
            order by c.nota asc
            """)
    List<Object[]> distribuicaoNotas(@Param("unidadeId") Long unidadeId,
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
