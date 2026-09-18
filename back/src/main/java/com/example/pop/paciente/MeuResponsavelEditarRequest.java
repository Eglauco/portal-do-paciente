package com.example.pop.paciente;

import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edição de uma pessoa autorizada pelo app: nome, data de nascimento, telefone e as permissões
 * por funcionalidade. O CPF (identidade de login) NÃO é editável aqui.
 */
public record MeuResponsavelEditarRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @NotNull LocalDate dataNascimento,
        @NotBlank @Size(max = 20) String telefone,
        Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes) {
}
