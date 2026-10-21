package com.example.pop.credencialia;

import java.time.LocalDateTime;

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
 * Credencial de IA (Claude/Anthropic) de um escopo, no schema do inquilino. {@code unidadeId} nulo =
 * PADRÃO do inquilino; preenchido = de uma unidade. A {@code apiKey} é guardada CIFRADA (SegredoCripto).
 * Os modelos por frente são opcionais (em branco → usa o padrão da plataforma).
 */
@Entity
@Table(name = "configuracao_ia_unidade")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unidade dona da credencial; NULL = padrão do inquilino. */
    @Column(name = "unidade_id")
    private Long unidadeId;

    /** API key da Anthropic, CIFRADA (nunca em claro). */
    @Column(name = "api_key", nullable = false, columnDefinition = "text")
    private String apiKey;

    @Column(name = "modelo_moderacao", length = 80)
    private String modeloModeracao;

    @Column(name = "modelo_chat", length = 80)
    private String modeloChat;

    @Column(name = "modelo_prontuario", length = 80)
    private String modeloProntuario;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
