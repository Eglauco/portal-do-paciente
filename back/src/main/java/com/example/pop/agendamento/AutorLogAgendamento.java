package com.example.pop.agendamento;

/** Quem realizou a troca de status registrada no log do agendamento. */
public enum AutorLogAgendamento {
    /** O próprio paciente, pelo app. */
    PACIENTE,
    /** Um responsável agindo pelo perfil dependente do paciente, pelo app. */
    RESPONSAVEL,
    /** A unidade (atendente do back-office). */
    UNIDADE
}
