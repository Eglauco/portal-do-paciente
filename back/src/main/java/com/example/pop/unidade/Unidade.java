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
}
