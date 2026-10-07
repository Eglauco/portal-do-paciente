package com.example.pop.agendamento;

import java.time.LocalDateTime;
import java.time.LocalTime;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "Horário" = a MARCAÇÃO de um paciente dentro de uma {@link Agenda} (nível {@code ID_AGE_CONSULTA_HOR} do CROSS).
 * Guarda o paciente, o status + histórico, os destinatários da notificação e a falta. A data/hora específica da
 * marcação fica aqui ({@code dataHora}, timestamp cheio = dia da agenda + hora de início; {@code horaFim} opcional).
 *
 * <p>Os campos de SLOT (especialidade, profissional, procedimento, unidade) moram na {@link Agenda}; os getters
 * {@code getEspecialidade()/getProfissionalSaude()/getProcedimento()/getUnidadeSaude()} aqui delegam para a agenda
 * (conveniência para o código que lê — a agenda é EAGER). Era a entidade {@code Horario}.
 */
@Entity
@Table(name = "horario")
@Getter
@Setter
@NoArgsConstructor
public class Horario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A sessão (slot) a que esta marcação pertence. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "agenda_id", nullable = false)
    private Agenda agenda;

    /**
     * Código do horário no sistema externo (CROSS {@code ID_AGE_CONSULTA_HOR}/{@code ID_AGE_EXAME_HOR}). Único por
     * TIPO quando preenchido. Chave de rastreio da marcação importada do SIRESP; só-leitura. Null nas marcações manuais.
     */
    @Column(name = "codigo_integracao", length = 60)
    private String codigoIntegracao;

    /**
     * Tipo de atendimento (CONSULTA/EXAME) — diferencia o {@code codigoIntegracao} entre os id-spaces do CROSS, que
     * podem colidir numericamente. A identidade do horário importado é (tipoAtendimento + codigoIntegracao). Nulo nas
     * marcações manuais (sem código).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_atendimento", length = 20)
    private TipoAtendimento tipoAtendimento;

    /** Data e hora da marcação (timestamp cheio = dia da agenda + hora de início). */
    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    /** Hora de término (opcional; do CROSS HOR_FIM). */
    @Column(name = "hora_fim")
    private LocalTime horaFim;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

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
    @JoinTable(name = "horario_motivo_falta",
            joinColumns = @JoinColumn(name = "horario_id"),
            inverseJoinColumns = @JoinColumn(name = "motivo_falta_id"))
    private List<MotivoFalta> motivosFalta = new ArrayList<>();

    /**
     * Resumo da entrega da notificação ao destino (paciente/responsáveis), aferido no
     * disparo do novo horário. Nulo = sem dado (anterior à funcionalidade / não disparado).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entrega_resumo", length = 30)
    private EstadoEntrega entregaResumo;

    // ---------- Getters delegados para a Agenda (campos de SLOT) ----------
    // A entidade usa acesso por CAMPO (anotações nos campos), então o Hibernate IGNORA estes métodos manuais —
    // são apenas conveniência para o código Java que lê. Quem ESCREVE slot deve mexer na Agenda diretamente.

    public Especialidade getEspecialidade() {
        return agenda == null ? null : agenda.getEspecialidade();
    }

    public ProfissionalSaude getProfissionalSaude() {
        return agenda == null ? null : agenda.getProfissionalSaude();
    }

    public Procedimento getProcedimento() {
        return agenda == null ? null : agenda.getProcedimento();
    }

    public Unidade getUnidadeSaude() {
        return agenda == null ? null : agenda.getUnidadeSaude();
    }
}
