package com.example.pop.prontuario;

import java.time.LocalDateTime;

/** Termo a assinar exibido ao paciente no app (nome + situação). */
public record TermoAssinaturaResponse(Long id, String nome, String status, String statusDescricao,
        LocalDateTime criadoEm) {

    public static TermoAssinaturaResponse from(TermoAssinatura t) {
        return new TermoAssinaturaResponse(t.getId(), t.getNome(), t.getStatus().name(),
                t.getStatus().getDescricao(), t.getCriadoEm());
    }
}
