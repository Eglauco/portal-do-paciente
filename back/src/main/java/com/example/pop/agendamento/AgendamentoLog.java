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
@Table(name = "agendamento_log")
@Getter
@Setter
@NoArgsConstructor
public class AgendamentoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutorLogAgendamento autor;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 40)
    private StatusAgendamento statusNovo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
