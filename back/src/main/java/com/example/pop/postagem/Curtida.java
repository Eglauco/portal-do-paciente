package com.example.pop.postagem;

import java.time.LocalDateTime;

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

/** Curtida de um aparelho (id anônimo) em uma postagem. Única por dispositivo. */
@Entity
@Table(name = "curtida", uniqueConstraints = @UniqueConstraint(columnNames = { "postagem_id", "dispositivo_id" }))
@Getter
@Setter
@NoArgsConstructor
public class Curtida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "postagem_id", nullable = false)
    private Postagem postagem;

    @Column(name = "dispositivo_id", nullable = false, length = 80)
    private String dispositivoId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
