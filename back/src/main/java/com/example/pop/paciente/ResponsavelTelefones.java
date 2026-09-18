package com.example.pop.paciente;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Regra compartilhada (app e back-office): um responsável tem de ser uma pessoa DIFERENTE
 * do paciente — logo o telefone dele não pode ser nenhum telefone da lista do paciente.
 * Compara sempre por dígitos normalizados.
 */
final class ResponsavelTelefones {

    private ResponsavelTelefones() {
    }

    /** Telefones (dígitos) do paciente: a lista de telefones, sem nulos/vazios. */
    static Set<String> doPaciente(Paciente p) {
        Set<String> tels = new LinkedHashSet<>();
        if (p.getTelefonesAdicionais() != null) {
            for (String t : p.getTelefonesAdicionais()) {
                adicionar(tels, t);
            }
        }
        return tels;
    }

    /** O telefone (dígitos) é de algum número do próprio paciente? */
    static boolean ehDoPaciente(Paciente p, String telefoneDigitos) {
        return telefoneDigitos != null && !telefoneDigitos.isBlank() && doPaciente(p).contains(telefoneDigitos);
    }

    private static void adicionar(Set<String> destino, String bruto) {
        String digitos = Documentos.somenteDigitos(bruto);
        if (digitos != null && !digitos.isBlank()) {
            destino.add(digitos);
        }
    }
}
