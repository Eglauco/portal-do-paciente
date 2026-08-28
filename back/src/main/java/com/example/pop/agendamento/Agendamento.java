package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.pop.especialidade.Especialidade;
import com.example.pop.motivofalta.MotivoFalta;
import com.example.pop.paciente.Paciente;
import com.example.pop.procedimento.Procedimento;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agendamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "especialidade_id", nullable = false)
    private Especialidade especialidade;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "profissional_saude_id", nullable = false)
    private ProfissionalSaude profissionalSaude;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "procedimento_id", nullable = false)
    private Procedimento procedimento;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidadeSaude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_agendamento", nullable = false, length = 40)
    private StatusAgendamento statusAgendamento;

    /** Texto livre com o porquê da falta, informado pelo paciente no app. */
    @Column(name = "justificativa_falta", columnDefinition = "TEXT")
    private String justificativaFalta;

    /** Momento em que o paciente justificou a falta (nulo = ainda não justificada). */
    @Column(name = "falta_justificada_em")
    private LocalDateTime faltaJustificadaEm;

    /** Motivos da falta selecionados pelo paciente. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "agendamento_motivo_falta",
            joinColumns = @JoinColumn(name = "agendamento_id"),
            inverseJoinColumns = @JoinColumn(name = "motivo_falta_id"))
    private List<MotivoFalta> motivosFalta = new ArrayList<>();
}
