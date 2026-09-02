package com.example.pop.chat;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicUpdate;

import com.example.pop.paciente.Paciente;
import com.example.pop.unidade.Unidade;
import com.example.pop.usuario.Usuario;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
// @DynamicUpdate: cada UPDATE só grava as colunas que ESTA transação alterou.
// Sem isso, um envio concorrente (que não mexe em responsavel_id) regravaria a
// linha inteira e sobrescreveria um "assumir" já committado por outro atendente,
// revertendo o responsável (lost update). Como o chat ao vivo é livre (não é o
// fluxo por turnos do SAU), evitamos @Version para não recusar envios simultâneos.
@DynamicUpdate
@Table(name = "chat", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_paciente_unidade", columnNames = { "paciente_id", "unidade_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidadeSaude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusChat status;

    /** Admin (atendente) que assumiu a conversa; só ele pode enviar. Nulo = sem responsável. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
