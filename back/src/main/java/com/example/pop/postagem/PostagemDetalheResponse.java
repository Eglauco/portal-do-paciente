package com.example.pop.postagem;

import java.time.LocalDateTime;

import com.example.pop.common.Ref;

/** Detalhe da postagem (admin — edição). */
public record PostagemDetalheResponse(
        Long id,
        String titulo,
        String descricao,
        boolean mostrarTotalCurtidas,
        boolean habilitarComentarios,
        /** Validação de comentários novos por IA está ligada nesta postagem. */
        boolean validarComentariosIa,
        Ref unidadeSaude,
        String url,
        LocalDateTime criadoEm,
        long totalCurtidas,
        long totalComentarios,
        /**
         * Até quando o admin já tinha visto os comentários ANTES de abrir agora (marca "novo").
         * O front destaca comentários criados depois disto. Null = nunca abriu (tudo é novo).
         */
        LocalDateTime comentariosVistosEm) {
}
