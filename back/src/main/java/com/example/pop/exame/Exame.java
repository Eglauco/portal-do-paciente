package com.example.pop.exame;

import com.example.pop.procedimento.Procedimento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exame")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Exame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Código do exame em um sistema externo (integração). Único quando preenchido. */
    @Column(name = "codigo_integracao", length = 60)
    private String codigoIntegracao;

    /**
     * Procedimento vinculado ao exame — usado no LANÇAMENTO AUTOMÁTICO do agendamento na importação do SIRESP
     * (o XML do CROSS não traz procedimento; o exame importado é mapeado, por código, para o procedimento).
     * Opcional: um exame sem procedimento não gera agendamento automático (o diagnóstico acusa a falta). EAGER para
     * a entidade serializar o procedimento sem sessão aberta (open-in-view=false) e para o SIRESP lê-lo fora de tx.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "procedimento_id")
    private Procedimento procedimento;
}
