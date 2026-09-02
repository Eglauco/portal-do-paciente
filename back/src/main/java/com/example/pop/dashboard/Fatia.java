package com.example.pop.dashboard;

/** Uma fatia de distribuição por categoria/status (chave técnica + rótulo + valor). */
public record Fatia(String chave, String rotulo, long valor) {
}
