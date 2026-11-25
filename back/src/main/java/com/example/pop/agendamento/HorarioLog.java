package com.example.pop.agendamento;

import java.time.LocalDateTime;

import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.Responsavel;
import com.example.pop.usuario.Usuario;

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
 * Registro de auditoria de uma troca de status do agendamento (linha do tempo):
 * qual foi a transição e QUEM a fez. Só uma referência de ator é preenchida por
 * linha, conforme {@link #autor}. Os nomes vêm por JOIN (só o FK é guardado).
 *
 * <p>Não confundir os campos: aqui {@code responsavel_id} é o responsável DO
 * PACIENTE (perfil dependente) e {@code usuario_id} é o atendente do back-office.
 */
@Entity
@Table(name = "horario_log")
@Getter
@Setter
@NoArgsConstructor
public class HorarioLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horario_id", nullable = false)
    private Horario horario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutorLogHorario autor;

    /** Paciente que fez a troca (quando autor = PACIENTE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    /** Responsável que fez a troca em nome do paciente (quando autor = RESPONSAVEL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Responsavel responsavel;

    /** Atendente que fez a troca (quando autor = UNIDADE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** Status antes da troca (nulo na criação do agendamento). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 40)
    private StatusAgendamento statusAnterior;

    /** Status depois da troca. Nulo nas linhas de {@link #evento} (que não são troca de status). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", length = 40)
    private StatusAgendamento statusNovo;

    /**
     * Evento que não é troca de status (ex.: convite do app enviado por SMS). Nulo nas linhas de status.
     * Quando preenchido, {@link #statusAnterior}/{@link #statusNovo} ficam nulos.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "evento", length = 30)
    private EventoLogHorario evento;

    /** Complemento do evento para exibição (ex.: o número MASCARADO para onde foi o convite). */
    @Column(name = "detalhe", length = 120)
    private String detalhe;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
