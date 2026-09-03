package com.example.pop.notificacao;

import java.time.LocalDateTime;

import com.example.pop.paciente.Paciente;

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
 * Notificação enviada a um paciente, guardada para ele ver na tela "Notificações"
 * (histórico do que foi notificado). Gravada sempre que o backend dispara um push,
 * independentemente de o push ter sido entregue.
 */
@Entity
@Table(name = "notificacao")
@Getter
@Setter
@NoArgsConstructor
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 400)
    private String corpo;

    /** Id do alvo para navegar ao tocar (ex.: manifestacaoId no SAU, postagemId). Nulo quando não há. */
    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(nullable = false)
    private boolean lida;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "lida_em")
    private LocalDateTime lidaEm;
}
