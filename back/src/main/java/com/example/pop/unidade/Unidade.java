package com.example.pop.unidade;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "unidade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Unidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Código da unidade em um sistema externo (integração SIRESP/CROSS). Único quando preenchido. */
    @Column(name = "codigo_integracao", length = 60)
    private String codigoIntegracao;

    // --- Perfil da unidade (estilo "perfil de empresa" do WhatsApp) -----------------------------
    // logo/capa guardam a URL pública do objeto no S3 (escopo da unidade); a leitura serve URL
    // pré-assinada. @JsonIgnore para o grid/export não vazarem a URL crua (o cadastro usa o DTO).
    @Column(name = "logo_url", length = 500)
    @JsonIgnore
    private String logoUrl;

    @Column(name = "capa_url", length = 500)
    @JsonIgnore
    private String capaUrl;

    @Column(columnDefinition = "TEXT")
    private String sobre;

    @Column(length = 40)
    private String telefone;

    @Column(columnDefinition = "TEXT")
    private String endereco;

    /** Coordenadas do endereço (geocodificadas no salvar); o app desenha o mapa nativo com elas. Nulas se não geocodificado. */
    private Double latitude;
    private Double longitude;

    /**
     * FAQ da unidade (base de conhecimento da IA do chat). Não serializado direto na entidade
     * (o grid/export de unidades não carregam FAQ); é exposto/gravado pelos DTOs do cadastro.
     * Coleção gerenciada com orphanRemoval: alterar SEMPRE pela própria lista (clear/add), nunca
     * trocando a referência, senão o Hibernate perde o rastreio dos órfãos.
     */
    @OneToMany(mappedBy = "unidade", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC, id ASC")
    @JsonIgnore
    private List<UnidadeFaq> faq = new ArrayList<>();

    /** Horário de funcionamento por dia da semana. Mesma regra de coleção do FAQ (clear/add). */
    @OneToMany(mappedBy = "unidade", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<UnidadeHorario> horarios = new ArrayList<>();
}
