package com.example.pop.siresp;

/**
 * Resposta do endpoint de importação: o resumo do import + o desfecho do envio automático ao Sistema de Gestão
 * (quando ligado). {@code enviado} = houve tentativa de POST; {@code envioSucesso} = o Sistema de Gestão processou;
 * {@code envioMensagem} = texto para o admin (null quando não houve envio).
 */
public record SirespImportResponse(
        int importados,
        String arquivo,
        int pacientesAtualizados,
        int pacientesCriados,
        boolean enviado,
        boolean envioSucesso,
        String envioMensagem) {
}
