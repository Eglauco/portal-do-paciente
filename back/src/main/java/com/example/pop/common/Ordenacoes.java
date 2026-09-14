package com.example.pop.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;

/**
 * Monta um {@link Sort} multi-coluna a partir dos parâmetros da tela ("campo,direcao"),
 * com WHITELIST de campos (segurança e evita erro por propriedade inexistente) e desempate
 * estável por "id" (paginação determinística). Reutilizável por qualquer listagem paginada.
 */
public final class Ordenacoes {

    private Ordenacoes() {
    }

    /**
     * @param ordenar    itens no formato "campoDaTela:asc|desc" (direção opcional; padrão asc).
     *                   O separador é ":" e NÃO vírgula: o Spring quebra na vírgula um
     *                   {@code List<String>} com uma única ocorrência, o que perderia a direção.
     * @param permitidos mapa campoDaTela → propriedade da entidade (whitelist)
     * @param padrao     ordenação usada quando nada válido é informado
     */
    public static Sort montar(List<String> ordenar, Map<String, String> permitidos, Sort padrao) {
        if (ordenar == null || ordenar.isEmpty()) {
            return padrao;
        }
        List<Sort.Order> ordens = new ArrayList<>();
        Set<String> usados = new HashSet<>();
        for (String item : ordenar) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String[] partes = item.split(":", 2); // limit 2: nunca devolve array vazio
            String chave = partes[0].trim();
            String propriedade = chave.isEmpty() ? null : permitidos.get(chave);
            if (propriedade == null || !usados.add(propriedade)) {
                continue; // campo em branco, não permitido ou repetido
            }
            boolean desc = partes.length > 1 && "desc".equalsIgnoreCase(partes[1].trim());
            ordens.add(desc ? Sort.Order.desc(propriedade) : Sort.Order.asc(propriedade));
        }
        if (ordens.isEmpty()) {
            return padrao;
        }
        // Desempate estável: registros com o mesmo valor não "trocam de lugar" entre páginas.
        if (usados.add("id")) {
            ordens.add(Sort.Order.asc("id"));
        }
        return Sort.by(ordens);
    }
}
