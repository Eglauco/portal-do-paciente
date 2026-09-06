package com.example.pop.paciente;

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
 * Conta do app: representa o TELEFONE autenticado por OTP e o aparelho vinculado.
 * É o ponto de sessão (cid no token). O perfil ativo (paciente por quem se age)
 * fica no claim pid. Assim um telefone que é só responsável também tem sessão.
 */
@Entity
@Table(name = "conta_app")
@Getter
@Setter
@NoArgsConstructor
public class ContaApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Telefone (somente dígitos). Único: uma conta por telefone. */
    @Column(nullable = false, length = 20)
    private String telefone;

    /** Aparelho com a sessão ativa (uma por vez). Trocar de aparelho exige reativar. */
    @Column(name = "dispositivo_ativo", length = 120)
    private String dispositivoAtivo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
