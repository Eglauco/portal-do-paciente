package com.example.pop.prontuario;

/** Estado da análise por IA de um documento do prontuário. */
public enum StatusAnaliseDocumento {

    /** Ainda não analisado (recém-subido, sem tipo/prompt, ou análise falhou e pode reanalisar). */
    NAO_ANALISADO("Não analisado"),
    /** A IA está processando o documento neste momento (status transitório, assíncrono). */
    EM_ANALISE("Análise em andamento"),
    /** Sem alteração: a IA não encontrou o critério de alerta, OU um humano confirmou que não há. */
    SEM_ALTERACOES("Sem alterações"),
    /** IA encontrou o critério de alerta → precisa da decisão humana (confirmar ou negar). */
    AGUARDANDO_VALIDACAO("Aguardando validação"),
    /** Um humano confirmou que o documento está realmente alterado (registra quem e quando). */
    ALTERACAO_CONFIRMADA("Alteração confirmada"),
    /** Formato não suportado pela IA (ex.: DICOM, .docx) — reanalisar não resolve. */
    NAO_ANALISAVEL("Não analisável");

    private final String descricao;

    StatusAnaliseDocumento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
