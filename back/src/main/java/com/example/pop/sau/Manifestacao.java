package com.example.pop.sau;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.pop.paciente.Paciente;
import com.example.pop.unidade.Unidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Manifestação do paciente ao SAU (elogio/crítica/sugestão) sobre uma unidade. */
@Entity
@Table(name = "manifestacao")
@Getter
@Setter
@NoArgsConstructor
public class Manifestacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidadeSaude;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoManifestacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusManifestacao status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /** Avaliação do atendimento (1-5), preenchida quando o paciente encerra e avalia. */
    @Column(name = "avaliacao_nota")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer avaliacaoNota;

    @Column(name = "avaliacao_comentario", length = 500)
    private String avaliacaoComentario;

    /** Quando foi avaliada. Não-nulo = encerrada e avaliada (definitiva, não reabre). */
    @Column(name = "avaliado_em")
    private LocalDateTime avaliadoEm;

    /**
     * Trava otimista: garante a regra de "1 mensagem por vez" sob concorrência.
     * Dois envios simultâneos do mesmo lado leem o mesmo status e passam pela
     * checagem, mas só um consegue gravar — o outro falha na versão e recebe 409.
     */
    @Version
    @Column(nullable = false)
    private Long versao;
}
