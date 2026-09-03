package com.example.pop.lembrete;

import java.time.LocalDateTime;

import com.example.pop.procedimento.Procedimento;

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

/** Lembrete de um procedimento: mensagem + antecedência (horas) do agendamento. */
@Entity
@Table(name = "lembrete")
@Getter
@Setter
@NoArgsConstructor
public class Lembrete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procedimento_id", nullable = false)
    private Procedimento procedimento;

    /** Texto mostrado ao paciente (push, pop-up e lista de notificações). */
    @Column(nullable = false, length = 300)
    private String texto;

    /** Horas antes do agendamento em que o lembrete deve ser disparado. */
    @Column(name = "horas_antecedencia", nullable = false)
    private Integer horasAntecedencia;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
