package com.example.pop.paciente;

import java.time.LocalDateTime;

/** Código de ativação gerado para o paciente (mostrado uma única vez ao admin). */
public record GerarCodigoResponse(String codigo, LocalDateTime expiraEm) {
}
