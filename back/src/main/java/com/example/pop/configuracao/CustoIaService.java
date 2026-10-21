package com.example.pop.configuracao;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.example.pop.modeloia.ModeloIa;
import com.example.pop.modeloia.ModeloIaRepository;

/**
 * Calcula o custo (US$) de um uso de IA a partir do modelo e dos tokens gastos, usando o preço por MTok
 * (milhão de tokens) do modelo no cadastro "Modelos de IA" (preço por VERSÃO exata). É um SNAPSHOT: quem
 * chama grava o resultado na própria linha (documento/mensagem/comentário), então mudar o preço depois só
 * afeta usos novos.
 *
 * <p>Devolve {@code null} quando não dá para calcular (tokens/modelo nulos ou modelo sem preço cadastrado) —
 * nesse caso o custo simplesmente não é registrado.
 */
@Service
public class CustoIaService {

    private static final BigDecimal MTOK = new BigDecimal(1_000_000);
    /** Casas decimais do custo em US$ (custos por uso ficam bem abaixo de 1 centavo). */
    private static final int ESCALA = 6;

    private final ModeloIaRepository modeloIaRepository;

    public CustoIaService(ModeloIaRepository modeloIaRepository) {
        this.modeloIaRepository = modeloIaRepository;
    }

    /** Custo em US$ (6 casas) do uso, ou null se não for possível calcular. */
    public BigDecimal custoUsd(String modelo, Long tokensEntrada, Long tokensSaida) {
        if (modelo == null || tokensEntrada == null || tokensSaida == null) {
            return null;
        }
        return modeloIaRepository.findByModeloId(modelo.trim())
                .map(m -> calcular(m, tokensEntrada, tokensSaida))
                .orElse(null); // modelo sem preço cadastrado → não calcula
    }

    private BigDecimal calcular(ModeloIa m, long tokensEntrada, long tokensSaida) {
        BigDecimal custo = BigDecimal.valueOf(tokensEntrada).multiply(m.getPrecoEntradaUsdMtok())
                .add(BigDecimal.valueOf(tokensSaida).multiply(m.getPrecoSaidaUsdMtok()));
        return custo.divide(MTOK, ESCALA, RoundingMode.HALF_UP);
    }
}
