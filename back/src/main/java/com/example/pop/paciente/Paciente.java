package com.example.pop.paciente;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "paciente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Telefone (somente dígitos) — chave do login no app. */
    @Column(length = 20)
    private String telefone;

    /** Liberado (globalmente) para acessar o app. */
    @Column(nullable = false)
    private boolean ativo = false;

    /** Hash BCrypt do código de ativação atual. Nunca serializado. */
    @JsonIgnore
    @Column(name = "codigo_ativacao_hash", length = 100)
    private String codigoAtivacaoHash;

    @JsonIgnore
    @Column(name = "codigo_ativacao_expira_em")
    private LocalDateTime codigoAtivacaoExpiraEm;

    /** Aparelho com a sessão ativa (uma por vez). Nunca serializado. */
    @JsonIgnore
    @Column(name = "dispositivo_ativo", length = 120)
    private String dispositivoAtivo;
}
