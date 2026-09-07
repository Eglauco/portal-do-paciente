package com.example.pop.paciente;

/**
 * Uma alteração (antes/depois) na linha do tempo. {@code campoDescricao} é o rótulo já
 * congelado; para linhas antigas (sem rótulo) cai num rótulo derivado do código.
 */
public record PacienteLogAlteracaoResponse(
        String campo,
        String campoDescricao,
        String valorAntes,
        String valorDepois) {

    public static PacienteLogAlteracaoResponse from(PacienteLogAlteracao a) {
        String rotulo = a.getRotulo() != null ? a.getRotulo() : rotuloDeFallback(a.getCampo());
        return new PacienteLogAlteracaoResponse(a.getCampo(), rotulo, a.getValorAntes(), a.getValorDepois());
    }

    /** Rótulo para registros antigos sem rótulo congelado: usa o enum escalar ou um mapa legado. */
    private static String rotuloDeFallback(String campo) {
        if (campo == null) {
            return null;
        }
        try {
            return CampoPaciente.valueOf(campo).getDescricao();
        } catch (IllegalArgumentException naoEhEscalar) {
            return switch (campo) {
                case "RESPONSAVEIS", "RESPONSAVEL" -> "Responsáveis";
                case "TELEFONES_ADICIONAIS", "TELEFONE_ADICIONAL" -> "Telefones adicionais";
                default -> campo;
            };
        }
    }
}
