package com.example.pop.perfil;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.example.pop.unidade.Unidade;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Perfil de acesso: um conjunto de telas liberadas + as unidades de saúde que o
 * perfil enxerga. Um usuário pode ter vários perfis (a permissão efetiva é a união).
 */
@Entity
@Table(name = "perfil")
@Getter
@Setter
@NoArgsConstructor
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Telas liberadas por este perfil. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfil_tela", joinColumns = @JoinColumn(name = "perfil_id"))
    @Column(name = "tela", length = 40, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Tela> telas = new HashSet<>();

    /** Unidades de saúde que este perfil enxerga (1 ou várias). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "perfil_unidade",
            joinColumns = @JoinColumn(name = "perfil_id"),
            inverseJoinColumns = @JoinColumn(name = "unidade_id"))
    private Set<Unidade> unidades = new HashSet<>();

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
