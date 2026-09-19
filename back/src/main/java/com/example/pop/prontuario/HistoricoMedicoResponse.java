package com.example.pop.prontuario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Resposta da tela Prontuário Médico: cabeçalho do paciente + resumo do histórico + linha do tempo. */
public record HistoricoMedicoResponse(
        PacienteCabecalho paciente,
        String resumoHistoricoIa,
        LocalDateTime resumoHistoricoGeradoEm,
        boolean iaHabilitada,
        boolean resumoIaHabilitado,
        List<ProntuarioAdminDetalheResponse> prontuarios) {

    /** Dados do paciente para o cabeçalho clínico da tela. */
    public record PacienteCabecalho(
            Long id,
            String nome,
            String cpf,
            LocalDate dataNascimento,
            String sexo,
            String fotoUrl,
            String prontuario,
            List<String> telefones,
            List<String> unidades,
            long alertasPendentes,
            int totalAtendimentos) {
    }
}
