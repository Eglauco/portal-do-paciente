package com.example.pop.credencialia;

/**
 * Qual credencial de IA (Claude/Anthropic) usar. A credencial é responsabilidade do CLIENTE e vive por
 * escopo: da UNIDADE, ou o PADRÃO do inquilino. Não existe nível de plataforma (é "nunca global"): sem
 * credencial configurada, cada frente de IA degrada como já faz hoje (moderação→pendente,
 * chat→escala humano, prontuário→indisponível).
 *
 * <p>UNIDADE sem config própria HERDA o padrão do inquilino; config própria INATIVA bloqueia (não herda).
 */
public record EscopoIa(Tipo tipo, Long unidadeId) {

    public enum Tipo { UNIDADE, INQUILINO_PADRAO }

    public static EscopoIa unidade(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("unidadeId é obrigatório para o escopo de unidade.");
        }
        return new EscopoIa(Tipo.UNIDADE, id);
    }

    public static EscopoIa inquilinoPadrao() {
        return new EscopoIa(Tipo.INQUILINO_PADRAO, null);
    }

    /** Escopo da unidade, ou o padrão do inquilino quando não há unidade (null-safe). */
    public static EscopoIa deUnidade(Long unidadeId) {
        return unidadeId == null ? inquilinoPadrao() : unidade(unidadeId);
    }

    /** Id estável (schema-independente) para a chave do cache por escopo. */
    public String cacheId() {
        return tipo == Tipo.UNIDADE ? "u" + unidadeId : "inq";
    }
}
