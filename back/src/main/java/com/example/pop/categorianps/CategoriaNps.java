package com.example.pop.categorianps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Categoria de NPS que o paciente avalia (ex.: Limpeza, Atendimento médico). */
@Entity
@Table(name = "categoria_nps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaNps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;
}
