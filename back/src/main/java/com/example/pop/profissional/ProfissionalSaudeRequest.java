package com.example.pop.profissional;

import java.time.LocalDate;
import java.util.List;

import com.example.pop.paciente.Sexo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação/edição de um profissional de saúde (back-office). Só o nome é
 * obrigatório. CPF/CNS/CEP/telefones chegam mascarados e são normalizados (só dígitos)
 * no controller; CPF e CNS têm dígito verificador. A foto vem como URL do objeto já
 * enviado ao S3 (upload pré-assinado no navegador). ativo/inativação são geridos por
 * inativar/reativar, não pelo corpo.
 */
public record ProfissionalSaudeRequest(
        @NotBlank @Size(min = 3, max = 120) String nome,
        Long conselhoId,
        @Size(max = 40) String numeroConselho,
        Sexo sexo,
        LocalDate dataNascimento,
        @Size(max = 20) String rg,
        @Size(max = 14) String cpf,
        @Size(max = 18) String cns,
        @Size(max = 20) String telefone,
        List<String> telefonesAdicionais,
        @Size(max = 160) String rua,
        @Size(max = 20) String numero,
        @Size(max = 120) String bairro,
        @Size(max = 120) String municipio,
        @Size(max = 2) String uf,
        @Size(max = 9) String cep,
        @Size(max = 160) String complemento,
        @Email @Size(max = 160) String email,
        @Size(max = 512) String fotoUrl,
        @Size(max = 60) String codigoIntegracao,
        /** Ids das especialidades que o profissional pode atender. */
        List<Long> especialidadeIds,
        /** Ids das unidades de saúde em que o profissional atende. */
        List<Long> unidadeIds) {

    /** Atalho (só nome) usado em testes e cadastros mínimos. */
    public ProfissionalSaudeRequest(String nome) {
        this(nome, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
}
