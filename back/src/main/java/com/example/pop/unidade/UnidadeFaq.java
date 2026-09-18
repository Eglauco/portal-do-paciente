package com.example.pop.unidade;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

/**
 * Item de FAQ de uma unidade (pergunta + resposta). Alimenta a assistente virtual do chat ao vivo:
 * a IA usa o FAQ da unidade em que o paciente abriu a conversa como base de conhecimento.
 */
@Entity
@Table(name = "unidade_faq")
@Getter
@Setter
@NoArgsConstructor
public class UnidadeFaq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unidade dona deste item; lado inverso, não serializado (evita ciclo no JSON). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    @JsonIgnore
    private Unidade unidade;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pergunta;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resposta;

    /** Ordem de exibição/uso (menor primeiro). */
    @Column(nullable = false)
    private int ordem;
}
