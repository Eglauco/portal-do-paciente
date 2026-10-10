package com.example.pop.plataforma;

/**
 * Identidade de plataforma enviada pelo super-admin (PUT). {@code corPrimaria} é obrigatória
 * ({@code #RRGGBB}); os demais campos em branco/{@code null} significam "usar o default". As URLs de
 * imagem são as CANÔNICAS (já enviadas ao S3 pelo upload), não as assinadas de visualização.
 */
public record ConfigPlataformaRequest(
        String corPrimaria,
        String nomePlataforma,
        String loginTitulo,
        String loginSubtitulo,
        String logoUrl,
        String loginFundoUrl) {
}
