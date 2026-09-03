package com.example.pop.procedimento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "procedimento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Procedimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "preparo", columnDefinition = "TEXT")
    private String preparo;

    /** Antecedência mínima (em horas) para o paciente poder cancelar o agendamento. */
    @NotNull
    @Min(0)
    @Column(name = "horas_cancelamento", nullable = false)
    private Integer horasCancelamento;

    /** Horas APÓS a presença do paciente para disparar o NPS (0 = na hora). */
    @NotNull
    @Min(0)
    @Column(name = "horas_nps", nullable = false)
    private Integer horasNps;
}
