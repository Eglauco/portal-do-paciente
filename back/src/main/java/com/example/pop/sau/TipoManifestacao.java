package com.example.pop.sau;

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
 * Tipo de manifestação do SAU (elogio, crítica, sugestão…) — agora um cadastro
 * do admin. Cada tipo tem um nome, uma descrição curta de ajuda (mostrada no app
 * abaixo do nome) e um flag {@code ativo}: quando desativado, deixa de aparecer
 * para o paciente selecionar, mas as manifestações já criadas continuam válidas.
 */
@Entity
@Table(name = "tipo_manifestacao")
@Getter
@Setter
@NoArgsConstructor
public class TipoManifestacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(length = 200)
    private String descricao;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
