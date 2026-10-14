package com.example.pop.pacienteauth;

import java.util.List;

/**
 * Resultado do login por senha. No app A-baked (ou após escolher a clínica) vem a {@code sessao}. No app
 * COMPARTILHADO, quando o CPF tem MAIS de uma clínica, vem {@code escolherClinica} (sem sessão ainda): o app
 * mostra o seletor e re-chama o login com o {@code inquilinoId} escolhido. Exatamente um dos dois é não-nulo.
 */
public record LoginSenhaResponse(AtivarResponse sessao, List<ClinicaLogin> escolherClinica) {
}
