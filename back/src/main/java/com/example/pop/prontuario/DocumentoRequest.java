package com.example.pop.prontuario;

import jakarta.validation.constraints.NotBlank;

/** Documento enviado no cadastro do prontuário (admin). tipoId direciona a análise por IA (opcional). */
public record DocumentoRequest(@NotBlank String nome, String url, Long tipoId) {
}
