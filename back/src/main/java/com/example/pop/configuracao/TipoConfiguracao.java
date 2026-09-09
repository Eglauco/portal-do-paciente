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
    COR
}
