package com.example.pop.agendaimportacao;

/**
 * Uma linha da tabela "HORÁRIOS" do preview (a marcação de um paciente). {@code linha} é o número da
 * linha na planilha (1-based) para o cliente localizar e corrigir. Cada campo é um {@link CampoPreview}.
 */
public record HorarioPreview(
        int linha,
        CampoPreview paciente,
        CampoPreview cpf,
        CampoPreview horaInicio,
        CampoPreview horaFim,
        CampoPreview status) {
}
