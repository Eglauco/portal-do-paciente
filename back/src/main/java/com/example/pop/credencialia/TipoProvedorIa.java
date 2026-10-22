package com.example.pop.credencialia;

/**
 * Provedores de IA plugáveis. Hoje só {@link #ANTHROPIC} está IMPLEMENTADO (as frentes usam LangChain4j);
 * os demais são GANCHOS da arquitetura. Plugar um provedor novo = adicionar o módulo
 * {@code langchain4j-<provedor>} ao pom, um branch no {@code ProvedorIaFactory} e semear os modelos
 * do provedor no cadastro "Modelos de IA". O front só oferece provedores que tenham modelos ativos.
 */
public enum TipoProvedorIa {
    /** Claude (Anthropic) — implementado. */
    ANTHROPIC,
    /** OpenAI (GPT) — gancho (ainda não implementado). */
    OPENAI,
    /** Google Gemini — gancho. */
    GOOGLE_GEMINI,
    /** OpenAI via Azure (usa base URL própria) — gancho. */
    AZURE_OPENAI;

    /** Converte com tolerância; null/inválido → ANTHROPIC (default seguro, mantém o comportamento atual). */
    public static TipoProvedorIa de(String valor) {
        if (valor == null || valor.isBlank()) {
            return ANTHROPIC;
        }
        try {
            return TipoProvedorIa.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ANTHROPIC;
        }
    }
}
