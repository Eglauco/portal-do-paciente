package com.example.pop.storage;

import jakarta.validation.constraints.NotBlank;

public record UploadUrlRequest(
        @NotBlank String nomeArquivo,
        String contentType) {
}
