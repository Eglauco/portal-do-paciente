package com.example.pop.nps;

import java.time.LocalDateTime;

import com.example.pop.agendamento.Agendamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Nps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false, unique = true)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusNps status;

    /** Nota de 0 a 10 (nula enquanto não respondido). */
    @Column
    private Integer nota;

    /** Observação opcional do paciente. */
    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;
}
