package com.example.pop.paciente;

/**
 * Situação do CADASTRO do paciente (soft-delete). Não confundir com {@code Paciente.ativo},
 * que é a liberação de ACESSO AO APP (código/OTP). Um paciente INATIVO fica somente-leitura
 * e some dos seletores/pesquisas de novos vínculos (os registros históricos permanecem).
 */
public enum SituacaoCadastro {
    ATIVO,
    INATIVO
}
