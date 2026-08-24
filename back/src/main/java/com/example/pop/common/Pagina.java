package com.example.pop.common;

import java.util.List;

/**
 * Estrutura de resposta paginada, reutilizável pelos CRUDs.
 */
public record Pagina<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
}
