package com.example.pop.agendamento;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado da ENTREGA da notificação de um agendamento a um destinatário (o paciente ou
 * um responsável). Uma linha por pessoa, gravada no momento do disparo. nome/telefone são
 * congelados para o histórico não depender do estado atual do responsável/paciente.
 */
@Entity
@Table(name = "agendamento_entrega")
@Getter
@Setter
@NoArgsConstructor
public class AgendamentoEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDestinatario tipo;

    /** Id do responsável quando tipo = RESPONSAVEL (nulo para o próprio paciente). */
    @Column(name = "responsavel_id")
    private Long responsavelId;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoEntrega estado;

    /**
     * Receipt ids "ok" da Expo aguardando confirmação de entrega (separados por vírgula).
     * Preenchido no envio quando a pessoa ficou NOTIFICACAO_ENVIADA; o job de receipts consome
     * e zera ao resolver (entregue/falhou) ou ao desistir (>24h). NULL = nada pendente.
     */
    @Column(name = "receipts_pendentes", columnDefinition = "TEXT")
    private String receiptsPendentes;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
