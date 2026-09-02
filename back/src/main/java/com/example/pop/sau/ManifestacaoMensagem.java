package com.example.pop.sau;

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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma mensagem da thread de uma manifestação (paciente ou SAU). Guarda autor,
 * data/hora e, quando é do SAU, qual admin (usuarioId + nome no momento) respondeu
 * — para auditoria.
 */
@Entity
@Table(name = "manifestacao_mensagem")
@Getter
@Setter
@NoArgsConstructor
public class ManifestacaoMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifestacao_id", nullable = false)
    private Manifestacao manifestacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutorManifestacao autor;

    /** Admin que respondeu (quando autor = SAU); nulo nas mensagens do paciente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
