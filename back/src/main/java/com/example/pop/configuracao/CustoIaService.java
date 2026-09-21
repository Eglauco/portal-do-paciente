package com.example.pop.configuracao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import org.springframework.stereotype.Service;

/**
 * Calcula o custo (US$) de um uso de IA a partir do modelo e dos tokens gastos, usando os preços
 * por MTok (milhão de tokens) cadastrados nas Configurações. É um SNAPSHOT: quem chama grava o
 * resultado na própria linha (documento/mensagem/comentário), então mudar o preço depois só afeta
 * usos novos.
 *
 * <p>Devolve {@code null} quando não dá para calcular (tokens/modelo nulos, modelo sem preço
 * cadastrado, ou preço ausente) — nesse caso o custo simplesmente não é registrado.
 */
@Service
public class CustoIaService {

    private static final BigDecimal MTOK = new BigDecimal(1_000_000);
    /** Casas decimais do custo em US$ (custos por uso ficam bem abaixo de 1 centavo). */
    private static final int ESCALA = 6;

    private final ConfiguracaoService configuracaoService;

    public CustoIaService(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    /** Custo em US$ (6 casas) do uso, ou null se não for possível calcular. */
    public BigDecimal custoUsd(String modelo, Long tokensEntrada, Long tokensSaida) {
        if (modelo == null || tokensEntrada == null || tokensSaida == null) {
            return null;
        }
        String[] chaves = chavesPreco(modelo);
        if (chaves == null) {
            return null; // modelo sem tabela de preço cadastrada
        }
        BigDecimal precoEntrada = lerPreco(chaves[0]);
        BigDecimal precoSaida = lerPreco(chaves[1]);
        if (precoEntrada == null || precoSaida == null) {
            return null;
        }
        BigDecimal custo = BigDecimal.valueOf(tokensEntrada).multiply(precoEntrada)
                .add(BigDecimal.valueOf(tokensSaida).multiply(precoSaida));
        return custo.divide(MTOK, ESCALA, RoundingMode.HALF_UP);
    }

    /** Mapeia o id do modelo para as chaves [entrada, saída] de preço; null se desconhecido. */
    private String[] chavesPreco(String modelo) {
        String m = modelo.toLowerCase(Locale.ROOT);
        if (m.contains("opus")) {
            return new String[] {
                    ChaveConfiguracao.CUSTO_IA_OPUS_5_ENTRADA_USD_MTOK,
                    ChaveConfiguracao.CUSTO_IA_OPUS_5_SAIDA_USD_MTOK };
        }
        if (m.contains("haiku")) {
            return new String[] {
                    ChaveConfiguracao.CUSTO_IA_HAIKU_45_ENTRADA_USD_MTOK,
                    ChaveConfiguracao.CUSTO_IA_HAIKU_45_SAIDA_USD_MTOK };
        }
        return null;
    }

    private BigDecimal lerPreco(String chave) {
        try {
            return configuracaoService.lerNumerico(chave);
        } catch (RuntimeException e) {
            return null; // chave ausente/tipo divergente → não calcula
        }
    }
}
