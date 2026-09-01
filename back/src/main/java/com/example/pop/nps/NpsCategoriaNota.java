package com.example.pop.nps;

import com.example.pop.categorianps.CategoriaNps;

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

/** Nota em estrelas (1 a 5) dada pelo paciente a uma categoria de NPS dentro de uma avaliação. */
@Entity
@Table(name = "nps_categoria_nota")
@Getter
@Setter
@NoArgsConstructor
public class NpsCategoriaNota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nps_id", nullable = false)
    private Nps nps;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "categoria_nps_id", nullable = false)
    private CategoriaNps categoria;

    @Column(nullable = false)
    private int nota;
}
