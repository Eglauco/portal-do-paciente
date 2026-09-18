package com.example.pop.paciente;

/**
 * Responsável que o paciente adicionou (visto na tela "Pessoas autorizadas" do app).
 * {@code ativo} indica se está liberado (inativo = sem acesso, mas preservado); {@code podeExcluir}
 * é false quando ele já tem lançamentos no sistema — aí só cabe inativar/reativar, não excluir.
 */
public record MeuResponsavelResponse(Long id, String nome, String cpf, java.time.LocalDate dataNascimento,
        String telefone, boolean ativo, boolean podeExcluir,
        java.util.Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes) {

    static MeuResponsavelResponse from(Responsavel r, boolean podeExcluir) {
        return new MeuResponsavelResponse(r.getId(), r.getNome(), r.getCpf(), r.getDataNascimento(),
                r.getTelefone(), r.isAtivo(), podeExcluir, r.getPermissoes());
    }
}
