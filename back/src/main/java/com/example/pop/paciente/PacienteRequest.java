package com.example.pop.paciente;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação/edição de um paciente (back-office). Só o nome é obrigatório.
 * A liberação de acesso ao app (ativo/código/aparelho) é gerida por
 * gerar-codigo/revogar, não pelo corpo. CPF/CNS/CEP/telefones chegam mascarados
 * e são normalizados (só dígitos) no controller; CPF e CNS têm dígito verificador.
 */
public record PacienteRequest(
        @NotBlank @Size(min = 3, max = 120) String nome,
        @Size(max = 20) String telefone,
        @Size(max = 60) String codigoIntegracao,
        @Size(max = 60) String prontuario,
        Sexo sexo,
        LocalDate dataNascimento,
        @Size(max = 20) String rg,
        @Size(max = 14) String cpf,
        @Size(max = 120) String nomeMae,
        @Size(max = 120) String nomePai,
        @Size(max = 160) String rua,
        @Size(max = 20) String numero,
        @Size(max = 120) String bairro,
        @Size(max = 120) String municipio,
        @Size(max = 2) String uf,
        @Size(max = 9) String cep,
        @Size(max = 160) String complemento,
        @Email @Size(max = 160) String email,
        @Size(max = 18) String cns,
        List<String> telefonesAdicionais) {

    /** Atalho (nome + telefone) usado em testes e cadastros mínimos. */
    public PacienteRequest(String nome, String telefone) {
        this(nome, telefone, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}

