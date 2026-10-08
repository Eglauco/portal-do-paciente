package com.example.pop.agendaimportacao;

import java.util.List;

/**
 * Preview completo da importação de UMA agenda a partir de uma planilha Excel (Fase 1 — nada é persistido).
 * Traz o cabeçalho da agenda + todas as linhas de horário, cada uma com seus campos validados/resolvidos, e o
 * total de erros. O front monta um resumo da agenda + a tabela de horários, destacando as células com erro para
 * o cliente corrigir a planilha e reimportar. A confirmação da importação (gravar) vem na Fase 2.
 */
public record AgendaImportPreviewResponse(
        String arquivo,
        AgendaPreview agenda,
        List<HorarioPreview> horarios,
        int totalHorarios,
        int totalErros) {

    /** Sem erros e com ao menos um horário — o que a Fase 2 vai exigir para liberar o "Confirmar importação". */
    public boolean semErros() {
        return totalErros == 0 && !horarios.isEmpty();
    }
}
