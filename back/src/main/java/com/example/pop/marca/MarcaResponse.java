package com.example.pop.marca;

/**
 * Textos de marca (white-label) lidos publicamente na tela de login, ANTES do login.
 * Espelha o padrão do tema: um contrato enxuto, só com o necessário para pintar a tela.
 */
public record MarcaResponse(
        /** Nome da plataforma (login, trocar unidade, sidebar, título da aba, rodapé de PDF). */
        String nomePlataforma,
        /** Título grande do painel de marca do login. */
        String loginTitulo,
        /** Subtítulo (texto de apoio) do login. */
        String loginSubtitulo,
        /** URL (assinada) da logomarca; null → o front usa a logo padrão (SVG). */
        String logoUrl,
        /** URL (assinada) da imagem de fundo do login; null → só o azul. */
        String loginFundoUrl) {
}
