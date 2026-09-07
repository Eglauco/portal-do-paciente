package com.example.pop.paciente;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Item da linha do tempo de auditoria do cadastro: o evento (tipo, quem, quando) e os
 * campos que mudaram. {@code autorNome} é o nome do responsável pela ação (o atendente
 * do back-office); nulo = ação sem ator identificado (exibir como "Sistema").
 */
public record PacienteLogResponse(
        Long id,
        TipoEventoPaciente tipo,
        String tipoDescricao,
        AutorLogPaciente autor,
        String autorNome,
        LocalDateTime criadoEm,
        List<PacienteLogAlteracaoResponse> alteracoes) {

    public static PacienteLogResponse from(PacienteLog l) {
        return new PacienteLogResponse(
                l.getId(),
                l.getTipo(),
                l.getTipo() != null ? l.getTipo().getDescricao() : null,
                l.getAutor(),
                autorNome(l),
                l.getCriadoEm(),
                l.getAlteracoes().stream().map(PacienteLogAlteracaoResponse::from).toList());
    }

    private static String autorNome(PacienteLog l) {
        if (l.getUsuario() != null) {
            return l.getUsuario().getNome();
        }
        if (l.getResponsavel() != null) {
            return l.getResponsavel().getNome();
        }
        if (l.getPacienteAtor() != null) {
            return l.getPacienteAtor().getNome();
        }
        return null;
    }
}
