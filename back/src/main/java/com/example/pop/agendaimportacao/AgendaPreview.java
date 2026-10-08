package com.example.pop.agendaimportacao;

/**
 * Bloco "DADOS DA AGENDA" do preview (o slot — preenchido uma vez por arquivo). Cada campo é um
 * {@link CampoPreview} com o valor digitado + resultado da resolução contra os cadastros existentes
 * (profissional, especialidade, Configuração da Agenda, unidade) ou da validação de formato (data).
 */
public record AgendaPreview(
        CampoPreview data,
        CampoPreview profissional,
        CampoPreview especialidade,
        CampoPreview configuracaoAgenda,
        CampoPreview unidade,
        CampoPreview nome) {
}
