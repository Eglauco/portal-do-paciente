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

    /** Id gerado pelo cliente (idempotência): evita duplicar em reenvios. */
    @Column(name = "cliente_id", length = 60)
    private String clienteId;
}
