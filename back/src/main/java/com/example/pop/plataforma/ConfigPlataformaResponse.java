package com.example.pop.plataforma;

/**
 * Identidade de plataforma devolvida ao super-admin (GET/PUT). Para cada imagem traz DUAS URLs: a
 * {@code *Url} CANÔNICA (para o front reenviar no PUT sem perder a imagem) e a {@code *UrlVisualizacao}
 * ASSINADA (GET pré-assinado, para o preview na tela — o bucket é privado). Os valores vêm CRUS (como
 * salvos; {@code null} quando o super-admin limpou o campo) — a tela trata vazio como "usa o default".
 */
public record ConfigPlataformaResponse(
        String corPrimaria,
        String nomePlataforma,
        String loginTitulo,
        String loginSubtitulo,
        String logoUrl,
        String logoUrlVisualizacao,
        String loginFundoUrl,
        String loginFundoUrlVisualizacao) {
}
