package com.example.pop.prontuario;

/** Estado da análise por IA de um documento do prontuário. */
public enum StatusAnaliseDocumento {

    /** Ainda não analisado (recém-subido, sem tipo/prompt, ou análise falhou e pode reanalisar). */
    NAO_ANALISADO("Não analisado"),
    /** IA analisou e não encontrou o critério de alerta. */
    SEM_ALTERACOES("Sem alterações"),
    /** IA encontrou o critério de alerta → precisa de validação humana. */
    AGUARDANDO_VALIDACAO("Aguardando validação"),
    /** Um humano validou o alerta (registra quem e quando). */
    VALIDADO("Validado"),
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
