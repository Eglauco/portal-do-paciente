package com.example.pop.paciente;

/**
 * Quem realizou o evento de auditoria do cadastro. UNIDADE = atendente do back-office;
 * PACIENTE = o próprio paciente pelo app (ex.: adicionar/remover pessoa autorizada);
 * RESPONSAVEL fica reservado para eventos originados por um responsável no app. SISTEMA
 * cobre ações sem ator identificado (ex.: sem token).
 */
public enum AutorLogPaciente {
    UNIDADE,
    PACIENTE,
    RESPONSAVEL,
    SISTEMA
}
