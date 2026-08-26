package com.example.pop.storage;

/**
 * uploadUrl: URL pré-assinada (PUT) para o navegador enviar o arquivo ao S3.
 * publicUrl: URL do objeto para salvar no documento.
 */
public record UploadUrlResponse(String uploadUrl, String publicUrl) {
}
