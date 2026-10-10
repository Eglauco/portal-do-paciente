package com.example.pop.plataforma;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuração de PLATAFORMA (identidade default: cor, logomarca, imagem de fundo e frases do login),
 * gerenciada pelo SUPER-ADMIN. Vive SEMPRE no schema {@code public} ({@code @Table(schema="public")}),
 * igual às demais tabelas de plataforma ({@code inquilino}/{@code usuario_login}) — é endereçada no
 * public independentemente do {@code search_path} do inquilino.
 *
 * <p>Mesmo padrão "chave → valor tipado" da {@code Configuracao} por-inquilino, mas enxuto: só as
 * colunas de valor usadas (cor/texto/imagem) e SEM a FK {@code atualizado_por → usuario} (o editor é o
 * super-admin, e o public não tem a tabela {@code usuario}). As chaves são as de
 * {@code com.example.pop.configuracao.ChaveConfiguracao} (COR_PRIMARIA_PLATAFORMA, NOME_PLATAFORMA,
 * LOGIN_TITULO, LOGIN_SUBTITULO, LOGO_PLATAFORMA, LOGIN_FUNDO).
 */
@Entity
@Table(name = "configuracao_plataforma", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador único da configuração (ex.: "COR_PRIMARIA_PLATAFORMA"). */
    @Column(nullable = false, unique = true, length = 100)
    private String chave;

    /** Valor do tipo COR: hex {@code #RRGGBB}. */
    @Column(name = "valor_cor", length = 9)
    private String valorCor;

    /** Valor do tipo TEXTO (nome/título/subtítulo do login). */
    @Column(name = "valor_texto", columnDefinition = "TEXT")
    private String valorTexto;

    /** Valor do tipo IMAGEM: URL canônica do objeto no S3 (pasta "plataforma"). */
    @Column(name = "valor_imagem", columnDefinition = "TEXT")
    private String valorImagem;

    /** Quando o valor foi alterado pela última vez. */
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    /** Id (livre) de quem alterou — sem FK (editor = super-admin, não um Usuario do inquilino). */
    @Column(name = "atualizado_por")
    private Long atualizadoPor;
}
