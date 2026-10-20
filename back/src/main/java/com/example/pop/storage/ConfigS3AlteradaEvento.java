package com.example.pop.storage;

/**
 * Publicado quando a config de S3 de um inquilino muda (salvar/ativar). O {@link StorageService}
 * ouve e invalida seu cache de clients/presigners para aquele schema, pra pegar os novos endpoints/
 * credenciais na próxima operação.
 */
public record ConfigS3AlteradaEvento(String schema) {
}
