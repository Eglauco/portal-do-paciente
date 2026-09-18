package com.example.pop.paciente;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados que o PRÓPRIO paciente (ou um responsável com "Ver e lançar" em Meu Perfil) pode
 * alterar pelo app. NÃO inclui campos de identidade/administrativos: CPF (identidade de
 * login), código de integração e prontuário (vêm da gestão) e unidades de acesso — esses
 * seguem geridos pelo back-office. CNS/CEP/telefones chegam mascarados e são normalizados
 * (só dígitos) no controller; o CNS tem dígito verificador.
 */
public record MeuPerfilRequest(
        @NotBlank @Size(min = 3, max = 120) String nome,
        List<String> telefonesAdicionais,
        Sexo sexo,
        LocalDate dataNascimento,
        @Size(max = 20) String rg,
        @Size(max = 18) String cns,
        @Size(max = 120) String nomeMae,
        @Size(max = 120) String nomePai,
        @Email @Size(max = 160) String email,
        @Size(max = 160) String rua,
        @Size(max = 20) String numero,
        @Size(max = 160) String complemento,
        @Size(max = 120) String bairro,
        @Size(max = 120) String municipio,
        @Size(max = 2) String uf,
        @Size(max = 9) String cep) {
}
