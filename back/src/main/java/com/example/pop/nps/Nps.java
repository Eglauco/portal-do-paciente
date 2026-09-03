package com.example.pop.nps;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.pop.agendamento.Agendamento;

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
import jakarta.persistence.OneToMany;
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

    /** Nota única (legado). As avaliações novas usam notas por categoria + média. */
    @Column
    private Integer nota;

    /** Média das notas por categoria (nula enquanto não respondido). */
    @Column
    private Double media;

    /** Notas dadas pelo paciente a cada categoria de NPS (carregadas sob demanda, no detalhe). */
    @OneToMany(mappedBy = "nps", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<NpsCategoriaNota> notasCategorias = new ArrayList<>();

    /** Observação opcional do paciente. */
    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;

    /** Momento agendado para disparar o NPS ao paciente (presença + horas do procedimento). */
    @Column(name = "disparar_em")
    private LocalDateTime dispararEm;

    /** Quando foi realmente disparado (visível/enviado ao paciente). Nulo = ainda agendado. */
    @Column(name = "disparado_em")
    private LocalDateTime disparadoEm;
}
