package com.example.pop.paciente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação/edição de um paciente. A liberação de acesso ao app
 * (ativo/código/aparelho) é gerida por gerar-codigo/revogar, não pelo corpo,
 * então esses campos ficam fora do request de propósito.
 */
public record PacienteRequest(
        @NotBlank @Size(min = 3, max = 120) String nome,
        @Size(max = 20) String telefone) {
}
