package com.example.pop.assinatura;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;

/**
 * Escolhe o {@link AssinaturaProvider} ativo conforme a Configuração {@code PROVEDOR_ASSINATURA}, e
 * expõe o modo de lote da Autentique ({@code AUTENTIQUE_MODO_LOTE}). Os webhooks são específicos por
 * provedor, então usam {@link #porId} (o webhook precisa do provedor que criou o documento, não do ativo).
 */
@Component
public class AssinaturaProviderFactory {

    private final List<AssinaturaProvider> provedores;
    private final ConfiguracaoService configuracaoService;

    public AssinaturaProviderFactory(List<AssinaturaProvider> provedores, ConfiguracaoService configuracaoService) {
        this.provedores = provedores;
        this.configuracaoService = configuracaoService;
    }

    /** Provedor ativo (Configuração PROVEDOR_ASSINATURA). Fallback ZapSign se o valor for inválido. */
    public AssinaturaProvider ativo() {
        return porId(lerAtivo());
    }

    /** Provedor por id — usado pelos receptores de webhook (um endpoint por provedor). */
    public AssinaturaProvider porId(ProvedorAssinatura id) {
        return provedores.stream().filter(p -> p.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Provedor de assinatura não registrado: " + id));
    }

    /**
     * true quando o lote deve virar UM único PDF combinado (senão, um documento por termo). Lê a config de
     * modo de lote do PROVEDOR ATIVO (a ZapSign ignora: sempre usa cerimônia única com extra-docs).
     */
    public boolean combinarLote() {
        String chave = switch (lerAtivo()) {
            case AUTENTIQUE -> ChaveConfiguracao.AUTENTIQUE_MODO_LOTE;
            case CLICKSIGN -> ChaveConfiguracao.CLICKSIGN_MODO_LOTE;
            case DOCUSIGN -> ChaveConfiguracao.DOCUSIGN_MODO_LOTE;
            default -> null; // ZapSign
        };
        if (chave == null) {
            return false;
        }
        String v = configuracaoService.lerTexto(chave);
        return v != null && "COMBINADO".equalsIgnoreCase(v.trim());
    }

    private ProvedorAssinatura lerAtivo() {
        String v = configuracaoService.lerTexto(ChaveConfiguracao.PROVEDOR_ASSINATURA);
        if (v == null || v.isBlank()) {
            return ProvedorAssinatura.ZAPSIGN;
        }
        try {
            return ProvedorAssinatura.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ProvedorAssinatura.ZAPSIGN;
        }
    }
}
