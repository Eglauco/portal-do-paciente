package com.example.pop.sau;

import java.time.LocalDateTime;

/**
 * Uma mensagem da thread. {@code autorNome} depende de quem lê: para o paciente,
 * as do SAU aparecem como "Atendimento SAU"; para o admin, o nome do atendente
 * que respondeu (auditoria).
 */
public record MensagemSauResponse(
        Long id,
        AutorManifestacao autor,
        String autorNome,
        String texto,
        LocalDateTime criadoEm) {
}
