package com.example.pop.motivofalta;

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

/** Motivo da falta do paciente em um agendamento (cadastro do admin). */
@Entity
@Table(name = "motivo_falta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MotivoFalta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String motivo;

    @Column(nullable = false)
    private boolean ativo = true;
}
