package com.example.pop.prontuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tipo de documento do prontuário (ex.: "Exame de sangue", "Laudo de patologia"). Guarda os prompts
 * que direcionam a IA: {@code promptResumo} (sem ele a IA não roda) e {@code promptValidacao}
 * (opcional; sem ele a IA nunca marca "Aguardando validação").
 */
@Entity
@Table(name = "tipo_documento_prontuario")
@Getter
@Setter
@NoArgsConstructor
public class TipoDocumentoProntuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Prompt que direciona o RESUMO clínico. Vazio/nulo = a IA não analisa este tipo. */
    @Column(name = "prompt_resumo", columnDefinition = "TEXT")
    private String promptResumo;

    /** Prompt que explica como identificar o ALERTA. Vazio/nulo = nunca marca "Aguardando validação". */
    @Column(name = "prompt_validacao", columnDefinition = "TEXT")
    private String promptValidacao;

    @Column(nullable = false)
    private boolean ativo = true;
}
