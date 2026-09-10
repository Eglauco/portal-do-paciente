package com.example.pop.paciente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo para o paciente adicionar um responsável pelo app (só nome + telefone). */
public record MeuResponsavelRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @NotBlank @Size(max = 20) String telefone) {
}
