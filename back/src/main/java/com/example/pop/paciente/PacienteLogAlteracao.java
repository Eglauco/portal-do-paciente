package com.example.pop.paciente;

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
 * Alteração de UM item dentro de um evento de auditoria (valor antes/depois). Pode ser
 * um campo escalar (nome, CPF…), a adição/remoção de um telefone adicional, ou uma
 * mudança em um responsável (nome, telefone ou uma permissão específica).
 */
@Entity
@Table(name = "paciente_log_alteracao")
@Getter
@Setter
@NoArgsConstructor
public class PacienteLogAlteracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "log_id", nullable = false)
    private PacienteLog log;

    /** Código estável do item alterado (ex.: NOME, CPF, RESPONSAVEL, TELEFONE_ADICIONAL). */
    @Column(nullable = false, length = 40)
    private String campo;

    /**
     * Rótulo humano JÁ "congelado" para exibição (ex.: "Responsável João Jr — Rede social").
     * Preservado no momento da gravação para não depender do estado atual do responsável.
     */
    @Column(length = 255)
    private String rotulo;

    /** Valor antes (nulo na criação, ao adicionar, ou quando o campo estava vazio). */
    @Column(name = "valor_antes", columnDefinition = "TEXT")
    private String valorAntes;

    /** Valor depois (nulo ao remover, ou quando o campo foi esvaziado). */
    @Column(name = "valor_depois", columnDefinition = "TEXT")
    private String valorDepois;
}
