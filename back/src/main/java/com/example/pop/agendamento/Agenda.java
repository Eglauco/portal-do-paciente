package com.example.pop.agendamento;

import java.time.LocalDate;

import com.example.pop.especialidade.Especialidade;
import com.example.pop.procedimento.Procedimento;
import com.example.pop.profissional.ProfissionalSaude;
import com.example.pop.unidade.Unidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * o profissional, a especialidade, o procedimento e a unidade. Uma Agenda tem N {@link Horario} (os pacientes
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
    @JoinColumn(name = "procedimento_id", nullable = false)
    private Procedimento procedimento;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidadeSaude;
}
