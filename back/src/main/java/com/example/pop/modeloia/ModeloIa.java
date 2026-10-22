package com.example.pop.modeloia;

import java.math.BigDecimal;

import com.example.pop.credencialia.TipoProvedorIa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cadastro dos modelos de IA (Claude/Anthropic) disponíveis na plataforma: cada linha é a VERSÃO exata
 * de um modelo com o seu preço (US$/MTok de entrada e saída). É a fonte única de verdade para:
 * <ul>
 *   <li>os <b>selects</b> de modelo no cadastro da credencial (sem erro de digitação);</li>
 *   <li>o <b>custo</b> de IA — o {@code CustoIaService} busca o preço pelo {@code modeloId} EXATO.</li>
 * </ul>
 * Por isso o preço é obrigatório: um modelo ativo sempre tem como calcular o custo.
 */
@Entity
@Table(name = "modelo_ia")
@Getter
@Setter
@NoArgsConstructor
public class ModeloIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Provedor a que o modelo pertence (Claude→ANTHROPIC, GPT→OPENAI…). Define o filtro no select da credencial. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "provedor", nullable = false, length = 30)
    private TipoProvedorIa provedor = TipoProvedorIa.ANTHROPIC;

    /** Id do modelo na API do provedor (ex.: {@code claude-opus-5}, {@code gpt-4o}). Único. */
    @NotBlank
    @Column(name = "modelo_id", nullable = false, length = 80, unique = true)
    private String modeloId;

    /** Nome amigável para exibir no select (ex.: "Claude Opus 5"). */
    @NotBlank
    @Column(nullable = false, length = 80)
    private String nome;

    @NotNull
    @PositiveOrZero
    @Column(name = "preco_entrada_usd_mtok", nullable = false, precision = 12, scale = 4)
    private BigDecimal precoEntradaUsdMtok;

    @NotNull
    @PositiveOrZero
    @Column(name = "preco_saida_usd_mtok", nullable = false, precision = 12, scale = 4)
    private BigDecimal precoSaidaUsdMtok;

    @Column(nullable = false)
    private boolean ativo = true;
}
