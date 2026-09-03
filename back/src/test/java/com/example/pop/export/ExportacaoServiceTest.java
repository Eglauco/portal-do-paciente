package com.example.pop.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExportacaoServiceTest {

    private final ExportacaoService service = new ExportacaoService();

    private record Linha(String nome, int idade) {
    }

    private static final List<ColunaExport<Linha>> COLUNAS = List.of(
            ColunaExport.de("Nome", Linha::nome),
            ColunaExport.de("Idade", l -> String.valueOf(l.idade())));

    @Test
    void geraExcelEPdfValidos() {
        List<Linha> dados = List.of(new Linha("Ana", 30), new Linha("Bruno", 25));

        byte[] xlsx = service.excel("Teste", COLUNAS, dados);
        assertTrue(xlsx.length > 0);
        // .xlsx é um zip → assinatura "PK".
        assertEquals('P', (char) xlsx[0]);
        assertEquals('K', (char) xlsx[1]);

        byte[] pdf = service.pdf("Teste", COLUNAS, dados);
        assertTrue(pdf.length > 0);
        // PDF começa com "%PDF".
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
    }

    @Test
    void geraComListaVazia() {
        assertTrue(service.excel("Vazio", COLUNAS, List.of()).length > 0);
        assertTrue(service.pdf("Vazio", COLUNAS, List.of()).length > 0);
    }
}
