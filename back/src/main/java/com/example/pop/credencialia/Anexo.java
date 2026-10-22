package com.example.pop.credencialia;

/**
 * Anexo multimodal de uma requisição de IA (ex.: o PDF/imagem da análise de documento do prontuário).
 * {@code mimeType} ex.: {@code application/pdf}, {@code image/png}, {@code image/jpeg}. Provider-agnostic:
 * cada {@code ProvedorIa} traduz para o formato nativo do seu LLM.
 */
public record Anexo(byte[] bytes, String mimeType) {

    public boolean ehPdf() {
        return mimeType != null && mimeType.toLowerCase().contains("pdf");
    }
}
