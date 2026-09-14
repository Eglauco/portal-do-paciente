package com.example.pop.conselho;

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

@Entity
@Table(name = "conselho")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Conselho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sigla do conselho (ex.: CRM), separada do nome. */
    @Column(nullable = false, length = 20)
    private String sigla;

    @Column(nullable = false, length = 120)
    private String nome;
}
