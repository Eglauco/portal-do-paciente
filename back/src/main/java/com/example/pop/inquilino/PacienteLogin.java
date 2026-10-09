package com.example.pop.inquilino;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Roteamento de login do PACIENTE/responsável (ponteiro cpf → inquilino). Mora SEMPRE no schema
 * {@code public} (tabela de plataforma). Só guarda o CPF e o inquilino — o cadastro e a credencial
 * (ContaApp/Paciente) ficam no schema do inquilino; o app resolve o inquilino aqui e autentica lá.
 */
@Entity
@Table(name = "paciente_login", schema = "public")
public class PacienteLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 11)
    private String cpf;

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

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
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
