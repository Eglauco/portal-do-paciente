package com.example.pop.postagem;

import java.time.LocalDateTime;
import java.util.List;

public record ComentarioResponse(
        Long id,
        String autor,
        String texto,
        LocalDateTime criadoEm,
        List<ComentarioResponse> respostas) {

    /** Comentário/resposta sem filhos aninhados. */
    public static ComentarioResponse from(Comentario c) {
        return new ComentarioResponse(c.getId(), c.getAutor(), c.getTexto(), c.getCriadoEm(), List.of());
    }

    /** Comentário-raiz com suas respostas (as respostas não aninham mais níveis). */
    public static ComentarioResponse from(Comentario c, List<Comentario> respostas) {
        List<ComentarioResponse> filhos = respostas.stream().map(ComentarioResponse::from).toList();
        return new ComentarioResponse(c.getId(), c.getAutor(), c.getTexto(), c.getCriadoEm(), filhos);
    }
}
