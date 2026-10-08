package com.example.pop.especialidade;

import com.example.pop.configuracaoagenda.ConfiguracaoAgenda;

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
@Table(name = "especialidade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Especialidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Código da especialidade em um sistema externo (integração). Único quando preenchido. */
    @Column(name = "codigo_integracao", length = 60)
    private String codigoIntegracao;

    /**
     * ConfiguracaoAgenda vinculado à especialidade — usado no LANÇAMENTO AUTOMÁTICO do agendamento na importação do SIRESP
     * (o XML do CROSS não traz configuracaoAgenda; antes era uma config global, agora é por especialidade). Opcional: uma
     * especialidade sem configuracaoAgenda não gera agendamento automático (o diagnóstico acusa a falta). EAGER para a
     * entidade serializar o configuracaoAgenda sem sessão aberta (open-in-view=false) e para o SIRESP lê-lo fora de tx.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "configuracao_agenda_id")
    private ConfiguracaoAgenda configuracaoAgenda;
}
