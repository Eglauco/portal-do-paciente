package com.example.pop.tema;

/**
 * Paleta do tema derivada da cor primária. Todos os campos são hex {@code #RRGGBB},
 * exceto {@code brandRgb} ("r, g, b") — usado no front para sombras com alfa
 * ({@code rgb(var(--brand-rgb) / 0.4)}).
 */
public record PaletaTema(
        /** Cor primária (semente clampada para faixa usável). */
        String brand,
        /** Tom escuro: hover, fim de gradiente, texto de link sobre claro. */
        String brandDeep,
        /** Tom bem escuro: painéis/backdrops escuros. */
        String brandPine,
        /** Tom claro: realces, strokes, marca d'água. */
        String glow,
        /** Cor do texto sobre a marca (branco ou escuro por luminância). */
        String onBrand,
        /** Canais "r, g, b" da marca, para rgb(var(--brand-rgb) / alfa). */
        String brandRgb,
        /** Fundo da aplicação: tom bem claro da marca (levemente tingido). */
        String bg) {
}
