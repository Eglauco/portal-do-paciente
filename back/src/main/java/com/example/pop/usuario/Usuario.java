package com.example.pop.usuario;

import java.util.HashSet;
import java.util.Set;

import com.example.pop.perfil.Perfil;
import com.example.pop.profissional.ProfissionalSaude;
import com.example.pop.unidade.Unidade;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 160)
    private String email;

    /** Hash BCrypt da senha. Nunca é serializado nas respostas da API. */
    @JsonIgnore
    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    /**
     * Momento (UTC) da última troca de senha. Tokens ADMIN emitidos ANTES disto são
     * rejeitados — trocar a senha derruba todas as sessões. Instant (não LocalDateTime)
     * para comparar direto com o "iat" do JWT, que é UTC.
     */
    @JsonIgnore
    @Column(name = "credenciais_alteradas_em")
    private java.time.Instant credenciaisAlteradasEm;

    /** Unidade de saúde ativa ("logada") do usuário. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;

    /** Perfis de acesso do usuário (a permissão efetiva é a união dos perfis). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_perfil",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "perfil_id"))
    private Set<Perfil> perfis = new HashSet<>();

    /**
     * Profissional de saúde que ESTE usuário representa (opcional): quando preenchido, o usuário "é" um
     * profissional e pode ver/assinar os termos (TCLE) dos atendimentos dele. LAZY + não serializado como
     * objeto (evita carregar o profissional inteiro); o front recebe só o id via {@link #getProfissionalSaudeId()}.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_saude_id")
    private ProfissionalSaude profissionalSaude;

    /** Id do profissional vinculado (para o front pré-selecionar), sem serializar o objeto inteiro. */
    @JsonProperty("profissionalSaudeId")
    public Long getProfissionalSaudeId() {
        return profissionalSaude == null ? null : profissionalSaude.getId();
    }
}
