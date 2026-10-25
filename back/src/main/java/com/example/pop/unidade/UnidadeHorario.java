package com.example.pop.unidade;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
 * Horário de funcionamento de uma unidade em um dia da semana (abre/fecha, ou fechado). Exibido no
 * perfil da unidade no app (estilo WhatsApp). Uma linha por dia; a ordem de exibição segue
 * {@link DayOfWeek#getValue()} (segunda=1 … domingo=7), ordenada ao montar a resposta.
 */
@Entity
@Table(name = "unidade_horario")
@Getter
@Setter
@NoArgsConstructor
public class UnidadeHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unidade dona deste horário; lado inverso, não serializado (evita ciclo no JSON). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    @JsonIgnore
    private Unidade unidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DayOfWeek diaSemana;

    /** Fechado neste dia (abertura/fechamento ficam nulos). */
    @Column(nullable = false)
    private boolean fechado;

    /** Aberta 24 horas neste dia (abertura/fechamento ficam nulos; o dia inteiro é aberto). */
    @Column(name = "vinte_quatro_horas", nullable = false)
    private boolean vinteQuatroHoras;

    private LocalTime abertura;
    private LocalTime fechamento;
}
