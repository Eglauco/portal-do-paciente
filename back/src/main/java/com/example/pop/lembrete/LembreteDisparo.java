package com.example.pop.lembrete;

import java.time.LocalDateTime;

import com.example.pop.agendamento.Agendamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Marca que um lembrete já foi disparado para um agendamento (dispara 1x por
 * agendamento). A UNIQUE (lembrete, agendamento) impede duplicidade sob corrida.
 */
@Entity
@Table(name = "lembrete_disparo", uniqueConstraints = @UniqueConstraint(columnNames = { "lembrete_id", "agendamento_id" }))
@Getter
@Setter
@NoArgsConstructor
public class LembreteDisparo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lembrete_id", nullable = false)
    private Lembrete lembrete;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
