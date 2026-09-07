package com.example.pop.paciente;

/**
 * Quem realizou o evento de auditoria do cadastro. Hoje o cadastro só é alterado pelo
 * back-office (UNIDADE); PACIENTE/RESPONSAVEL ficam reservados para eventos originados
 * no app no futuro. SISTEMA cobre ações sem ator identificado (ex.: sem token).
 */
public enum AutorLogPaciente {
    UNIDADE,
    PACIENTE,
    RESPONSAVEL,
    SISTEMA
}
