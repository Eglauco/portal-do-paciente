package com.example.pop.marca;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.plataforma.ConfiguracaoPlataformaService;
import com.example.pop.storage.StorageService;
import com.example.pop.tenant.TenantContext;

/**
 * Resolve os textos de marca (white-label) das configurações, com fail-safe para os
 * valores padrão (os mesmos hardcoded de antes). Mesmo espírito do {@code TemaService}:
 * se a config faltar/quebrar/estiver vazia, cai no padrão — a tela nunca fica sem texto.
 */
@Service
public class MarcaService {

    /** Padrões = os textos originais hardcoded do login (fail-safe). */
    static final String NOME_PADRAO = "Portal do Paciente";
    static final String TITULO_PADRAO = "A gestão do cuidado começa aqui.";
    static final String SUBTITULO_PADRAO =
            "Cadastre e acompanhe exames, consultas e informações dos pacientes — "
                    + "com segurança e agilidade no dia a dia da equipe.";

    /** Validade da URL assinada da logo — longa para o cache do front não servir link morto. */
    private static final Duration VALIDADE_LOGO = Duration.ofHours(24);

    private final ConfiguracaoService configuracaoService;
    private final ConfiguracaoPlataformaService plataformaService;
    private final StorageService storageService;

    public MarcaService(ConfiguracaoService configuracaoService, ConfiguracaoPlataformaService plataformaService,
            StorageService storageService) {
        this.configuracaoService = configuracaoService;
        this.plataformaService = plataformaService;
        this.storageService = storageService;
    }

    /**
     * Marca atual. NOME e LOGO vêm do inquilino (pós-login) com fallback à plataforma. Já TÍTULO,
     * SUBTÍTULO e FUNDO do login vêm SEMPRE da plataforma: só aparecem na tela de login, que é
     * pré-inquilino (ainda não se sabe o inquilino) — por isso nem são config por-inquilino (V154).
     */
    public MarcaResponse marca() {
        return new MarcaResponse(
                nomePlataforma(),
                textoPlataformaOu(ChaveConfiguracao.LOGIN_TITULO, TITULO_PADRAO),
                textoPlataformaOu(ChaveConfiguracao.LOGIN_SUBTITULO, SUBTITULO_PADRAO),
                logoUrl(),
                urlImagemPlataforma(ChaveConfiguracao.LOGIN_FUNDO));
    }

    /**
     * Marca SEMPRE da PLATAFORMA (ignora o inquilino), para o portão do super-admin. Todos os campos
     * saem da config de plataforma (ou do padrão hardcoded), independentemente de haver inquilino resolvido.
     */
    public MarcaResponse marcaPlataforma() {
        return new MarcaResponse(
                textoPlataformaOu(ChaveConfiguracao.NOME_PLATAFORMA, NOME_PADRAO),
                textoPlataformaOu(ChaveConfiguracao.LOGIN_TITULO, TITULO_PADRAO),
                textoPlataformaOu(ChaveConfiguracao.LOGIN_SUBTITULO, SUBTITULO_PADRAO),
                urlImagemPlataforma(ChaveConfiguracao.LOGO_PLATAFORMA),
                urlImagemPlataforma(ChaveConfiguracao.LOGIN_FUNDO));
    }

    /** URL assinada da logomarca (24h) ou null se não houver imagem/config — front cai no SVG. */
    public String logoUrl() {
        return urlImagem(ChaveConfiguracao.LOGO_PLATAFORMA);
    }

    /** Bytes da logomarca (server-side) para embutir no PDF; null se não houver/for inacessível. */
    public byte[] logoBytes() {
        String url = imagemBruta(ChaveConfiguracao.LOGO_PLATAFORMA);
        return url == null ? null : storageService.baixarBytes(url);
    }

    /** URL assinada (24h) da imagem da chave, ou null se não houver — fail-safe. */
    private String urlImagem(String chave) {
        String url = imagemBruta(chave);
        return url == null ? null : storageService.urlVisualizacao(url, VALIDADE_LOGO);
    }

    /** URL assinada (24h) da imagem SEMPRE da config de plataforma (nunca do inquilino), ou null. */
    private String urlImagemPlataforma(String chave) {
        String url = imagemPlataforma(chave);
        return url == null ? null : storageService.urlVisualizacao(url, VALIDADE_LOGO);
    }

    /** Texto SEMPRE da config de plataforma (nunca do inquilino) → ou o padrão hardcoded. */
    private String textoPlataformaOu(String chave, String padrao) {
        String valor = textoPlataforma(chave);
        return valor != null ? valor : padrao;
    }

    /**
     * URL canônica da imagem (não assinada); null se ausente. Resolve em dois níveis: pré-login lê a
     * imagem da PLATAFORMA (super-admin); logado tenta a do inquilino e, se vazia, cai na da plataforma.
     */
    private String imagemBruta(String chave) {
        if (preLogin()) {
            return imagemPlataforma(chave);
        }
        try {
            String url = configuracaoService.lerImagem(chave);
            if (url != null && !url.isBlank()) {
                return url;
            }
        } catch (RuntimeException e) {
            // config ausente/tipo divergente → cai na plataforma abaixo
        }
        return imagemPlataforma(chave);
    }

    /** Nome da plataforma (config ou padrão) — reaproveitado pelo rodapé dos relatórios. */
    public String nomePlataforma() {
        return lerTextoOu(ChaveConfiguracao.NOME_PLATAFORMA, NOME_PADRAO);
    }

    /**
     * Texto em dois níveis: pré-login = texto da PLATAFORMA (super-admin) ou o padrão; logado = texto do
     * inquilino → se vazio, texto da plataforma → se vazio, o padrão hardcoded. A tela nunca fica sem texto.
     */
    private String lerTextoOu(String chave, String padrao) {
        if (preLogin()) {
            String daPlataforma = textoPlataforma(chave);
            return daPlataforma != null ? daPlataforma : padrao;
        }
        try {
            String valor = configuracaoService.lerTexto(chave);
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        } catch (RuntimeException e) {
            // config ausente (faltou migration) ou tipo divergente → cai na plataforma/padrão abaixo
        }
        String daPlataforma = textoPlataforma(chave);
        return daPlataforma != null ? daPlataforma : padrao;
    }

    /** Texto da chave na config de PLATAFORMA (super-admin); null se vazio/ausente/erro. */
    private String textoPlataforma(String chave) {
        try {
            String valor = plataformaService.valorTexto(chave);
            return valor == null || valor.isBlank() ? null : valor;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** URL canônica da imagem da chave na config de PLATAFORMA (super-admin); null se vazia/ausente/erro. */
    private String imagemPlataforma(String chave) {
        try {
            String valor = plataformaService.valorImagem(chave);
            return valor == null || valor.isBlank() ? null : valor;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Pré-login = nenhum inquilino resolvido na thread ({@code atualBruto() == null}). Usa o valor CRU do
     * ThreadLocal (não {@code atual()}) para distinguir o pré-login do inquilino cujo schema é o padrão
     * ({@code principal}) — este, logado, deve ver o próprio branding, não o neutro.
     */
    private static boolean preLogin() {
        return TenantContext.atualBruto() == null;
    }
}
