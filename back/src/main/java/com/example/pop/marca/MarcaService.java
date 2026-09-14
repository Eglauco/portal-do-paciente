package com.example.pop.marca;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.storage.StorageService;

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
    private final StorageService storageService;

    public MarcaService(ConfiguracaoService configuracaoService, StorageService storageService) {
        this.configuracaoService = configuracaoService;
        this.storageService = storageService;
    }

    /** Textos + imagens de marca atuais (com fallback ao padrão em cada campo). */
    public MarcaResponse marca() {
        return new MarcaResponse(
                nomePlataforma(),
                lerTextoOu(ChaveConfiguracao.LOGIN_TITULO, TITULO_PADRAO),
                lerTextoOu(ChaveConfiguracao.LOGIN_SUBTITULO, SUBTITULO_PADRAO),
                logoUrl(),
                urlImagem(ChaveConfiguracao.LOGIN_FUNDO));
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

    /** URL canônica salva na config de imagem (não assinada); null se ausente/vazia/erro. */
    private String imagemBruta(String chave) {
        try {
            String url = configuracaoService.lerImagem(chave);
            return url == null || url.isBlank() ? null : url;
        } catch (RuntimeException e) {
            return null; // config ausente/tipo divergente → sem imagem
        }
    }

    /** Nome da plataforma (config ou padrão) — reaproveitado pelo rodapé dos relatórios. */
    public String nomePlataforma() {
        return lerTextoOu(ChaveConfiguracao.NOME_PLATAFORMA, NOME_PADRAO);
    }

    private String lerTextoOu(String chave, String padrao) {
        try {
            String valor = configuracaoService.lerTexto(chave);
            return valor == null || valor.isBlank() ? padrao : valor;
        } catch (RuntimeException e) {
            return padrao; // config ausente (faltou migration) ou tipo divergente → padrão
        }
    }
}
