package com.example.pop.configuracao;

import java.math.BigDecimal;

/**
 * Edição de uma configuração — SÓ o valor. nome/descrição/chave/tipo são definidos no
 * código e não mudam pela tela. O servidor aplica apenas o campo correspondente ao
 * {@code tipoConfiguracao} do registro; os demais são ignorados.
 */
public record ConfiguracaoRequest(
        Boolean valorBooleano,
        String valorTexto,
        BigDecimal valorNumerico,
        String valorCor,
        /** URL do objeto no S3 (tipo IMAGEM) — o front sobe o arquivo e envia a URL aqui. */
        String valorImagem) {
}
