package com.example.pop.paciente;

/**
 * Paciente enxuto para o typeahead de seleção (id + nome + CPF + prontuário, para desambiguar na lista).
 * O {@code nome} já é o NOME DE EXIBIÇÃO (nome social quando o paciente usa; senão, o civil) — a busca
 * ({@code buscarParaSelecao}) continua casando por nome civil OU social, mas a tela exibe o de exibição.
 */
public record PacienteSelecaoResponse(Long id, String nome, String nomeRegistro, boolean temNomeSocial,
        String cpf, String prontuario) {

    public static PacienteSelecaoResponse from(Paciente p) {
        boolean usa = p.isUtilizaNomeSocial() && p.getNomeSocial() != null && !p.getNomeSocial().isBlank();
        return new PacienteSelecaoResponse(p.getId(), p.nomeExibicao(), p.getNome(), usa, p.getCpf(), p.getProntuario());
    }
}
