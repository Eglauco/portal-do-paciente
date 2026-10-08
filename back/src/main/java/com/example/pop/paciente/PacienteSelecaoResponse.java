package com.example.pop.paciente;

/** Paciente enxuto para o typeahead de seleção (id + nome + CPF + prontuário, para desambiguar na lista). */
public record PacienteSelecaoResponse(Long id, String nome, String cpf, String prontuario) {

    public static PacienteSelecaoResponse from(Paciente p) {
        return new PacienteSelecaoResponse(p.getId(), p.getNome(), p.getCpf(), p.getProntuario());
    }
}
