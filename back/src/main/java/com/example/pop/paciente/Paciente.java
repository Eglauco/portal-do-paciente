package com.example.pop.paciente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    /** Telefone (somente dígitos) — chave do login no app. Único quando preenchido. */
    @Column(length = 20)
    private String telefone;

    /** Código do paciente em um sistema externo (integração). Único quando preenchido. */
    @Column(name = "codigo_integracao", length = 60)
    private String codigoIntegracao;

    /** Número do prontuário. Único quando preenchido. */
    @Column(length = 60)
    private String prontuario;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sexo sexo;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(length = 20)
    private String rg;

    /** CPF (somente dígitos). Único quando preenchido. */
    @Column(length = 11)
    private String cpf;

    @Column(name = "nome_mae", length = 120)
    private String nomeMae;

    @Column(name = "nome_pai", length = 120)
    private String nomePai;

    @Column(length = 160)
    private String rua;

    @Column(length = 20)
    private String numero;

    @Column(length = 120)
    private String bairro;

    @Column(length = 120)
    private String municipio;

    @Column(length = 2)
    private String uf;

    /** CEP (somente dígitos). */
    @Column(length = 8)
    private String cep;

    @Column(length = 160)
    private String complemento;

    @Column(length = 160)
    private String email;

    /** Cartão Nacional de Saúde (somente dígitos). Único quando preenchido. */
    @Column(length = 15)
    private String cns;

    /** Telefones adicionais (somente dígitos), além do principal. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "paciente_telefone", joinColumns = @JoinColumn(name = "paciente_id"))
    @Column(name = "numero", length = 20)
    private List<String> telefonesAdicionais = new ArrayList<>();

    /** Foto do paciente (URL do objeto no S3, pasta "foto-paciente"). Alterável pelo app. */
    @Column(name = "foto_url", length = 512)
    private String fotoUrl;

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
