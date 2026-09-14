package com.example.pop.profissional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.pop.conselho.Conselho;
import com.example.pop.especialidade.Especialidade;
import com.example.pop.paciente.Sexo;
import com.example.pop.unidade.Unidade;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "profissional")
@Getter
@Setter
@NoArgsConstructor
public class ProfissionalSaude {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Conselho de classe (ex.: CRM). Vínculo único, opcional. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conselho_id")
    private Conselho conselho;

    /** Número de registro no conselho (ex.: 12345). */
    @Column(name = "numero_conselho", length = 40)
    private String numeroConselho;

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

    /** Cartão Nacional de Saúde (somente dígitos). Único quando preenchido. */
    @Column(length = 15)
    private String cns;

    /** Telefone principal (somente dígitos). */
    @Column(length = 20)
    private String telefone;

    /** Telefones adicionais (somente dígitos), além do principal. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profissional_telefone", joinColumns = @JoinColumn(name = "profissional_id"))
    @Column(name = "numero", length = 20)
    private List<String> telefonesAdicionais = new ArrayList<>();

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

    /** Foto do profissional (URL do objeto no S3, pasta "foto-profissional"). */
    @Column(name = "foto_url", length = 512)
    private String fotoUrl;

    /** Código do profissional em um sistema externo (integração). Único quando preenchido. */
    @Column(name = "codigo_integracao", length = 60)
    private String codigoIntegracao;

    /**
     * Ativo: quando falso, o profissional NÃO aparece nas seleções de outras telas
     * (ex.: agendamento) nem nas regras de negócio; fica só no cadastro para histórico.
     */
    @Column(nullable = false)
    private boolean ativo = true;

    /** Data/hora em que foi inativado (nulo quando ativo). */
    @Column(name = "inativado_em")
    private LocalDateTime inativadoEm;

    /** Especialidades que o profissional pode atender. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "profissional_especialidade",
            joinColumns = @JoinColumn(name = "profissional_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidade_id"))
    @OrderBy("nome")
    private Set<Especialidade> especialidades = new LinkedHashSet<>();

    /** Unidades de saúde em que o profissional atende. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "profissional_unidade",
            joinColumns = @JoinColumn(name = "profissional_id"),
            inverseJoinColumns = @JoinColumn(name = "unidade_id"))
    @OrderBy("nome")
    private Set<Unidade> unidades = new LinkedHashSet<>();
}
