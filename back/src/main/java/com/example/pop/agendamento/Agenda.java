package com.example.pop.agendamento;

import java.time.LocalDate;

import com.example.pop.especialidade.Especialidade;
import com.example.pop.configuracaoagenda.ConfiguracaoAgenda;
import com.example.pop.profissional.ProfissionalSaude;
import com.example.pop.unidade.Unidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "Agenda" = o SLOT/sessão do profissional (nível superior do CROSS: {@code ID_AGE_CONSULTA}). Guarda o dia,
 * o profissional, a especialidade, o configuracaoAgenda e a unidade. Uma Agenda tem N {@link Horario} (os pacientes
 * marcados). A hora específica de cada marcação fica no {@link Horario} ({@code dataHora}/{@code horaFim}).
 */
@Entity
@Table(name = "agenda")
@Getter
@Setter
@NoArgsConstructor
public class Agenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Código da agenda no sistema externo (CROSS {@code ID_AGE_CONSULTA}). Único quando preenchido. É a chave que
     * agrupa os horários na importação do SIRESP: registros com o mesmo código reaproveitam esta agenda. Null nas
     * agendas manuais e nas importadas antes da Fase 4.
     */
    @Column(name = "codigo_integracao", length = 60)
    private String codigoIntegracao;

    /**
     * Tipo de atendimento (CONSULTA/EXAME) — diferencia o {@code codigoIntegracao} entre os id-spaces do CROSS, que
     * podem colidir numericamente. A identidade da agenda importada é (tipoAtendimento + codigoIntegracao). Nulo nas
     * agendas manuais (sem código).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_atendimento", length = 20)
    private TipoAtendimento tipoAtendimento;

    /**
     * Nome da agenda (rótulo). Vem do CROSS {@code AGE_CONSULTA_NOME} na importação do SIRESP, mas também pode ser
     * editado manualmente. Opcional.
     */
    @Column(name = "nome", length = 255)
    private String nome;

    /** Dia da sessão (hora local BR, zoneless). A hora de cada marcação fica no Horário. */
    @Column(name = "data", nullable = false)
    private LocalDate data;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "profissional_saude_id", nullable = false)
    private ProfissionalSaude profissionalSaude;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "especialidade_id", nullable = false)
    private Especialidade especialidade;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "configuracao_agenda_id", nullable = false)
    private ConfiguracaoAgenda configuracaoAgenda;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidadeSaude;
}
