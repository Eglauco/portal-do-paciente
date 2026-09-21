package com.example.pop.usoia;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ledger (livro-razão) de auditoria: UMA linha por chamada de IA, imutável, para conferir o gasto
 * com a fatura da plataforma. Guarda o consumo daquela chamada (tokens/modelo/custo), a descrição e
 * a rota do front para acessar a origem (prontuário, conversa, comentário). Complementa os
 * acumuladores que ficam nas entidades (documento/mensagem/comentário/paciente).
 */
@Entity
@Table(name = "uso_ia")
@Getter
@Setter
@NoArgsConstructor
public class UsoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UsoIaTipo tipo;

    /** Descrição legível do uso (ex.: "Documento: Exame de Lab 2"). */
    @Column(nullable = false, length = 200)
    private String descricao;

    /** Modelo de IA usado (ex.: claude-opus-5); nulo se não informado. */
    @Column(name = "modelo_ia", length = 60)
    private String modeloIa;

    @Column(name = "tokens_entrada")
    private Long tokensEntrada;

    @Column(name = "tokens_saida")
    private Long tokensSaida;

    /** Custo (US$) desta chamada, congelado no preço vigente; nulo se não calculável. */
    @Column(name = "custo_usd", precision = 12, scale = 6)
    private BigDecimal custoUsd;

    /** Rota do front para acessar a origem (ex.: "/prontuarios/45"); nulo se não aplicável. */
    @Column(length = 200)
    private String rota;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
