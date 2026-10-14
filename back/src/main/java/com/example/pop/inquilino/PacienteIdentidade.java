package com.example.pop.inquilino;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Credencial CENTRAL do paciente — a IDENTIDADE por CPF (Fase B). Mora SEMPRE no schema {@code public}
 * (tabela de plataforma, V157): 1 CPF = 1 PIN que serve TODAS as clínicas do paciente. A SESSÃO/aparelho
 * ({@code conta_app.dispositivo_ativo}) e o DADO CLÍNICO continuam por-inquilino. O {@code @Table(schema="public")}
 * garante que nunca seja resolvida pelo {@code search_path} de um inquilino.
 */
@Entity
@Table(name = "paciente_identidade", schema = "public")
public class PacienteIdentidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CPF (somente dígitos) — a identidade central (único no public). */
    @Column(nullable = false, length = 11)
    private String cpf;

    /** Hash (BCrypt) do PIN de 6 dígitos. Nulo = não definido / resetado por um novo OTP. */
    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    /** Tentativas consecutivas de PIN erradas; ao atingir o limite, entra em lockout temporal. Central. */
    @Column(name = "senha_tentativas", nullable = false)
    private int senhaTentativas;

    /** Lockout AUTO-EXPIRÁVEL: o login por senha fica bloqueado até este instante (null = livre). */
    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public int getSenhaTentativas() {
        return senhaTentativas;
    }

    public void setSenhaTentativas(int senhaTentativas) {
        this.senhaTentativas = senhaTentativas;
    }

    public LocalDateTime getBloqueadoAte() {
        return bloqueadoAte;
    }

    public void setBloqueadoAte(LocalDateTime bloqueadoAte) {
        this.bloqueadoAte = bloqueadoAte;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
