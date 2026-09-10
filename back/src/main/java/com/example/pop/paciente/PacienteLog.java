package com.example.pop.paciente;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.pop.usuario.Usuario;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento de auditoria do cadastro do paciente (uma ação de salvar): CRIACAO, ALTERACAO,
 * INATIVACAO ou REATIVACAO — quem fez e quando. Os campos alterados vêm em
 * {@link #alteracoes}. Só uma referência de ator é preenchida, conforme {@link #autor}:
 * UNIDADE → {@link #usuario}; PACIENTE → {@link #pacienteAtor} (ações do app, ex.:
 * adicionar/remover pessoa autorizada); SISTEMA → nenhuma.
 */
@Entity
@Table(name = "paciente_log")
@Getter
@Setter
@NoArgsConstructor
public class PacienteLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Paciente auditado. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEventoPaciente tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutorLogPaciente autor;

    /** Paciente que fez a ação pelo app (autor = PACIENTE), ex.: adicionar/remover pessoa autorizada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_ator_id")
    private Paciente pacienteAtor;

    /** Responsável que fez a ação pelo app (reservado para o futuro; hoje nulo). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Responsavel responsavel;

    /** Atendente do back-office que fez a ação (quando autor = UNIDADE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    /** Campos alterados neste evento (na criação, todos os preenchidos; antes = null). */
    @OneToMany(mappedBy = "log", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<PacienteLogAlteracao> alteracoes = new ArrayList<>();
}
