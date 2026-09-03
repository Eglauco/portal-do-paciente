package com.example.pop.sau;

import java.time.LocalDateTime;

/**
 * Uma mensagem da thread. {@code autorNome}: nas do paciente, o nome do paciente;
 * nas do SAU, o nome do atendente que respondeu (mostrado ao admin e ao paciente),
 * ou "Atendimento SAU" quando não há atendente vinculado.
 */
public record MensagemSauResponse(
        Long id,
        AutorManifestacao autor,
        String autorNome,
        String texto,
        LocalDateTime criadoEm) {
}
