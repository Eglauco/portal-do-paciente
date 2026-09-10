package com.example.pop.paciente;

/**
 * Quem criou o responsável. {@link #PACIENTE} = adicionado pelo próprio paciente no app
 * (escopo travado em AGENDAMENTOS); {@link #ADMIN} = cadastrado no back-office (controle total).
 * O paciente só gerencia (lista/remove) os de origem PACIENTE.
 */
public enum OrigemResponsavel {
    ADMIN,
    PACIENTE
}
