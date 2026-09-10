package com.example.pop.paciente;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Responsável de um paciente — cadastro paralelo e leve (nome + telefone), sem
 * relação com a tabela paciente. Pertence a um paciente e some junto com ele.
 */
@Entity
@Table(name = "responsavel")
@Getter
@Setter
@NoArgsConstructor
public class Responsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Paciente dono deste responsável. Nunca serializado (evita recursão no /paciente/{id}). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Telefone (somente dígitos). Opcional. */
    @Column(length = 20)
    private String telefone;

    /** Data de nascimento (opcional). Usada p/ validar idade mínima ao comentar na rede social. */
    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    /** Quem criou: ADMIN (back-office) ou PACIENTE (app; escopo travado em AGENDAMENTOS). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemResponsavel origem = OrigemResponsavel.ADMIN;

    /**
     * Ativo (soft-delete/acesso). Inativo = sem acesso ao perfil do paciente no app
     * (some do seletor e a próxima ação é negada), mas preservado para não perder a
     * autoria dos lançamentos já feitos. Reativável.
     */
    @Column(nullable = false)
    private boolean ativo = true;

    /**
     * Só para a tela (não persistido): indica se o responsável tem algum lançamento no
     * sistema. Quando true, a remoção é bloqueada — só resta inativar. Preenchido no
     * GET do paciente.
     */
    @Transient
    private boolean temLancamentos;

    /**
     * Nível de acesso do responsável por funcionalidade do app. Só guarda o que foi
     * concedido: uma funcionalidade ausente equivale a {@link NivelAcessoResponsavel#SEM_ACESSO}
     * (padrão). EAGER para acompanhar o responsável, que já é carregado com o paciente.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "responsavel_funcionalidade",
            joinColumns = @JoinColumn(name = "responsavel_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "funcionalidade", length = 30)
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 20)
    private Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes = new EnumMap<>(FuncionalidadeApp.class);
}
