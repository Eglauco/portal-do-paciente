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
 * Conta do app: representa o CPF autenticado por OTP e o aparelho vinculado.
 * É o ponto de sessão (cid no token). O perfil ativo (paciente por quem se age)
 * fica no claim pid. Assim um CPF que é só responsável também tem sessão. O telefone
 * deixou de ser identidade — é só o canal por onde o OTP é enviado.
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

    /** CPF (somente dígitos) — a identidade da conta (uma conta por CPF + aparelho). */
    @Column(nullable = false, length = 11)
    private String cpf;

    /** Aparelho com a sessão ativa (uma por vez). Trocar de aparelho exige reativar. */
    @Column(name = "dispositivo_ativo", length = 120)
    private String dispositivoAtivo;

    /**
     * Hash (BCrypt) do PIN de acesso definido pelo paciente/responsável — permite entrar sem SMS.
     * Nulo = ainda não definiu (ou foi resetado por um novo OTP). Login por senha exige a identidade
     * (telefone+CPF+data) + este PIN.
     */
    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    /** Tentativas consecutivas de PIN erradas; ao atingir o limite, o login por senha é bloqueado (usa SMS). */
    @Column(name = "senha_tentativas", nullable = false)
    private int senhaTentativas;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
