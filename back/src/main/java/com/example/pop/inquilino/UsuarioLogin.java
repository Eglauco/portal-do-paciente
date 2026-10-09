package com.example.pop.inquilino;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Roteamento de login do ADMIN (ponteiro email → inquilino). Mora SEMPRE no schema {@code public}
 * (tabela de plataforma). Só guarda o e-mail e o inquilino — a SENHA continua no {@code usuario}
 * do schema do inquilino (o {@code /auth/login} resolve o inquilino aqui e autentica lá).
 */
@Entity
@Table(name = "usuario_login", schema = "public")
public class UsuarioLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(name = "inquilino_id", nullable = false)
    private Long inquilinoId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getInquilinoId() {
        return inquilinoId;
    }

    public void setInquilinoId(Long inquilinoId) {
        this.inquilinoId = inquilinoId;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }
}
