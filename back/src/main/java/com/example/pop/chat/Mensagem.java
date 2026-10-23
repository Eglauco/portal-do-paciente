package com.example.pop.chat;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.pop.usuario.Usuario;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mensagem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RemetenteMensagem remetente;

    /** Atendente que enviou (quando remetente = UNIDADE); nulo nas mensagens do paciente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * Mensagem citada (feature "responder mensagem", estilo WhatsApp) — sempre do mesmo chat;
     * nula quando esta mensagem não é uma resposta. ON DELETE SET NULL no banco (V170).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responde_a_id")
    private Mensagem respondeA;

    /**
     * Responsável do paciente que enviou esta mensagem em nome dele (perfil
     * dependente no app); nulo quando foi o próprio paciente. Não confundir com
     * {@code chat.responsavel_id}, que é o atendente do back-office.
     */
    @Column(name = "responsavel_id")
    private Long responsavelId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "enviada_em", nullable = false)
    private LocalDateTime enviadaEm;

    /** A unidade já visualizou a mensagem do paciente (recibo de leitura interno). */
    @Column(nullable = false)
    private boolean lida;

    /** A mensagem já chegou no aparelho do destinatário (entrega — os "checks"). */
    @Column(nullable = false)
    private boolean entregue;

    /** Quando a mensagem foi entregue ao aparelho (2º check); nulo enquanto não entregue. */
    @Column(name = "entregue_em")
    private LocalDateTime entregueEm;

    /** Quando o paciente visualizou a mensagem da unidade (recibo "lido"); nulo enquanto não lida. */
    @Column(name = "visualizada_em")
    private LocalDateTime visualizadaEm;

    /** Id gerado pelo cliente (idempotência): evita duplicar em reenvios. */
    @Column(name = "cliente_id", length = 60)
    private String clienteId;

    /**
     * Mensagem da unidade gerada pela assistente virtual (IA), não por um atendente humano.
     * Só é true em mensagens com remetente = UNIDADE; sempre false nas do paciente.
     */
    @Column(name = "gerada_por_ia", nullable = false)
    private boolean geradaPorIa;

    /** Tokens gastos pela IA neste turno (só em mensagens geradaPorIa; nulos caso contrário). */
    @Column(name = "tokens_entrada")
    private Long tokensEntrada;

    @Column(name = "tokens_saida")
    private Long tokensSaida;

    /** Modelo de IA usado neste turno (ex.: claude-haiku-4-5); para cálculo de custo. */
    @Column(name = "modelo_ia", length = 60)
    private String modeloIa;

    /** Custo (US$) deste turno da IA, congelado no momento do uso; nulo se não calculável. */
    @Column(name = "custo_usd", precision = 12, scale = 6)
    private java.math.BigDecimal custoUsd;
}
