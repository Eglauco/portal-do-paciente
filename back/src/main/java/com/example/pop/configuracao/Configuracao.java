package com.example.pop.configuracao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * Configuração do sistema no padrão "chave → valor tipado". Cada registro nasce no
 * código (INSERT via migration com a {@code chave} única + valor padrão) e depois o
 * admin ajusta APENAS o valor pela tela. A regra de negócio busca pela {@code chave}
 * e usa o campo de valor correspondente ao {@code tipoConfiguracao}.
 */
@Entity
@Table(name = "configuracao")
@Getter
@Setter
@NoArgsConstructor
public class Configuracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Rótulo amigável mostrado na tela (definido no código/migration). */
    @Column(nullable = false, length = 120)
    private String nome;

    /** Ajuda curta explicando o que a configuração faz (opcional). */
    @Column(length = 400)
    private String descricao;

    /** Identificador único usado pela regra de negócio (ex.: "TEMPO_PARA_DISPARAR_MENSAGEM"). */
    @Column(nullable = false, unique = true, length = 100)
    private String chave;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_configuracao", nullable = false, length = 20)
    private TipoConfiguracao tipoConfiguracao;

    @Column(name = "valor_booleano")
    private Boolean valorBooleano;

    @Column(name = "valor_texto", columnDefinition = "TEXT")
    private String valorTexto;

    @Column(name = "valor_numerico", precision = 19, scale = 4)
    private BigDecimal valorNumerico;

    /** Valor do tipo COR: hex {@code #RRGGBB}. */
    @Column(name = "valor_cor", length = 9)
    private String valorCor;

    /** Auditoria leve: quando e por quem o valor foi alterado pela última vez. */
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    /** Id do usuário (admin) que fez a última alteração; null se nunca foi editado. */
    @Column(name = "atualizado_por")
    private Long atualizadoPor;
}
