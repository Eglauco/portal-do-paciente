package com.example.pop.paciente;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
        List<String> telefonesAdicionais,
        List<ResponsavelRequest> responsaveis,
        /** Ids das unidades de saúde que o paciente pode acessar (feed/chat/SAU). */
        List<Long> unidadeIds) {

    /** Atalho (nome + telefone) usado em testes e cadastros mínimos. */
    public PacienteRequest(String nome, String telefone) {
        this(nome, telefone, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    /** Atalho (nome + telefone + unidades de acesso) usado em testes. */
    public PacienteRequest(String nome, String telefone, List<Long> unidadeIds) {
        this(nome, telefone, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, unidadeIds);
    }

    /**
     * Responsável do paciente (cadastro paralelo). {@code id} nulo = novo; preenchido
     * = já existente (mantém o mesmo registro na edição). Só o nome é obrigatório.
     * {@code permissoes} define o nível de acesso por funcionalidade do app; ausente
     * ou SEM_ACESSO = sem acesso àquela funcionalidade (padrão).
     */
    public record ResponsavelRequest(
            Long id,
            @NotBlank @Size(min = 2, max = 120) String nome,
            @Size(max = 20) String telefone,
            Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes,
            /** Situação do responsável: ausente (null) = ativo. Inativo perde acesso ao app. */
            Boolean ativo) {

        /** Compat (testes/cadastros mínimos): sem permissões nem situação explícitas → SEM_ACESSO e ativo. */
        public ResponsavelRequest(Long id, String nome, String telefone) {
            this(id, nome, telefone, null, null);
        }

        /** Situação efetiva: ausente (null) = ativo (true). */
        public boolean ativoOuPadrao() {
            return ativo == null || ativo;
        }
    }
}

