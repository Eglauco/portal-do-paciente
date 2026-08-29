package com.example.pop.pacienteauth;

/** Sessão do paciente: token (na ativação) + dados básicos. */
public record PacienteSessaoResponse(String token, Long pacienteId, String nome) {
}
