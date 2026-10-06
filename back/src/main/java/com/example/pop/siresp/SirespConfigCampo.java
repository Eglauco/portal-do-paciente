package com.example.pop.siresp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Política de atualização de UM campo do paciente na importação do SIRESP (SEMPRE / SE_VAZIO / NUNCA).
 * Uma linha por campo (ver {@link CampoMapeado}); o liga/desliga geral fica na config SIRESP_ATUALIZAR_PACIENTE.
 */
@Entity
@Table(name = "siresp_config_campo")
@Getter
@Setter
@NoArgsConstructor
public class SirespConfigCampo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chave do campo (ex.: "nome", "cpf") — ver {@link CampoMapeado#CAMPOS}. */
    @Column(name = "campo", nullable = false, length = 40, unique = true)
    private String campo;

    @Enumerated(EnumType.STRING)
    @Column(name = "acao", nullable = false, length = 10)
    private AcaoAtualizacao acao = AcaoAtualizacao.NUNCA;
}
