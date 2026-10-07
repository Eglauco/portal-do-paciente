package com.example.pop.siresp;

/**
 * Tipo do registro importado do SIRESP/CROSS: {@link #CONSULTA} (layout "agendamento de consulta", campos
 * {@code *_CONSULTA}/protocolo) ou {@link #EXAME} (layout "agendamento de exame", campos {@code *_EXAME}/exame).
 * Os dois usam a MESMA tabela {@code siresp}; as colunas que não se aplicam ficam nulas. Detectado na importação
 * pela presença dos elementos do exame no XML.
 */
public enum TipoRegistroSiresp {

    CONSULTA("Consulta"),
    EXAME("Exame");

    private final String descricao;

    TipoRegistroSiresp(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
