package com.example.pop.lembrete;

import java.time.LocalDateTime;

/**
 * Pop-up de lembrete a mostrar ao abrir o app. Traz a mensagem e, quando o
 * agendamento ainda pode ser cancelado (confirmado e dentro do prazo), o
 * {@code podeCancelar}=true habilita o botão de cancelar no pop-up.
 */
public record LembretePopupResponse(
        Long id,
        String titulo,
        String mensagem,
        Long agendamentoId,
        boolean podeCancelar,
        LocalDateTime dataHora,
        String especialidade) {
}
