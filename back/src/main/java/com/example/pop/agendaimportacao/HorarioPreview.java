package com.example.pop.agendaimportacao;

/**
 * Uma linha da tabela "HORÁRIOS" do preview (a marcação de um paciente). {@code linha} é o número da
 * linha na planilha (1-based) para o cliente localizar e corrigir. O paciente é LOCALIZADO por
 * id/prontuário/código (nunca por nome); {@code paciente.resolvido} traz o nome encontrado. Toda marcação
 * entra como "Aguardando confirmação do paciente" (sem coluna de status na planilha).
 */
public record HorarioPreview(
        int linha,
        CampoPreview paciente,
        CampoPreview horaInicio,
        CampoPreview horaFim) {
}
