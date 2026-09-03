package com.example.pop.export;

import java.util.function.Function;

/** Uma coluna de exportação: título do cabeçalho + como extrair o valor (texto) de cada linha. */
public record ColunaExport<T>(String titulo, Function<T, String> valor) {

    public static <T> ColunaExport<T> de(String titulo, Function<T, String> valor) {
        return new ColunaExport<>(titulo, valor);
    }
}
