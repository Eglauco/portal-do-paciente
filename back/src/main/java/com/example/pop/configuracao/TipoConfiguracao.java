package com.example.pop.configuracao;

/**
 * Tipo do valor de uma configuração. Define qual coluna de valor é usada
 * (valorBooleano / valorNumerico / valorTexto) na regra de negócio e no formulário.
 */
public enum TipoConfiguracao {
    BOOLEANO,
    NUMERICO,
    TEXTO,
    /** Cor (hex {@code #RRGGBB}) guardada em {@code valorCor}; a tela mostra um seletor RGB. */
    COR,
    /** Imagem: a URL do objeto no S3 (pasta "configuracao") guardada em {@code valorImagem}; a tela sobe o arquivo. */
    IMAGEM,
    /**
     * Segredo (token/chave de integração) guardado CIFRADO em {@code valorSegredo} (AES-GCM, {@link SegredoCripto}).
     * É WRITE-ONLY: a API nunca devolve o valor em claro (só um flag "preenchido") e é OCULTO da tela genérica de
     * Configurações — editável apenas pela tela dedicada (ex.: Provedores de assinatura).
     */
    SEGREDO
}
