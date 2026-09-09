package com.example.pop.tema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Testa a derivação de paleta (lógica pura, sem contexto Spring). */
class TemaServiceTest {

    /** ConfiguracaoService não é usado por derivar(), só por tema(). */
    private final TemaService service = new TemaService(null);

    @Test
    void verdePadraoPreservaAMarcaEUsaTextoBranco() {
        PaletaTema p = service.derivar("#0E8C7F");
        // Semente dentro da faixa usável → a marca é preservada exatamente.
        assertEquals("#0E8C7F", p.brand());
        assertEquals("14, 140, 127", p.brandRgb());
        assertEquals("#FFFFFF", p.onBrand());
        // Tons derivados: deep e pine mais escuros que a marca; todos hex válidos.
        assertHex(p.brandDeep());
        assertHex(p.brandPine());
        assertHex(p.glow());
        assertHex(p.bg());
        assertTrue(luminancia(p.bg()) > 0.85, "o fundo é bem claro (tom levíssimo da marca)");
        assertTrue(luminancia(p.brandPine()) < luminancia(p.brandDeep()), "pine é mais escuro que deep");
        assertTrue(luminancia(p.brandDeep()) < luminancia(p.brand()), "deep é mais escuro que a marca");
        assertTrue(luminancia(p.glow()) > luminancia(p.brand()), "glow é mais claro que a marca");
    }

    @Test
    void sementeClaraEhEscurecidaParaGarantirContrasteComBranco() {
        // Amarelo bem claro reprovaria branco por cima; o clamp escurece.
        PaletaTema p = service.derivar("#FFF3B0");
        assertHex(p.brand());
        assertTrue(contrasteComBranco(p.brand()) >= 3.0,
                "branco sobre a marca deve atingir >= 3:1 (UI) mesmo para semente clara");
        assertEquals("#FFFFFF", p.onBrand());
    }

    @Test
    void brancoEPretoViramMarcasUsaveis() {
        assertTrue(contrasteComBranco(service.derivar("#FFFFFF").brand()) >= 3.0);
        PaletaTema preto = service.derivar("#000000");
        assertHex(preto.brand());
        assertTrue(contrasteComBranco(preto.brand()) >= 3.0);
    }

    @Test
    void sementeAcromaticaContinuaNeutra() {
        // Cinza puro (hue indefinido) deve virar tema cinza — não avermelhado.
        String brand = service.derivar("#808080").brand();
        int r = Integer.parseInt(brand.substring(1, 3), 16);
        int g = Integer.parseInt(brand.substring(3, 5), 16);
        int b = Integer.parseInt(brand.substring(5, 7), 16);
        assertEquals(r, g, "marca de um cinza deve ser neutra (R=G)");
        assertEquals(g, b, "marca de um cinza deve ser neutra (G=B)");
    }

    @Test
    void corInvalidaCaiNoVerdePadrao() {
        assertEquals("#0E8C7F", service.derivar("banana").brand());
        assertEquals("#0E8C7F", service.derivar(null).brand());
    }

    @Test
    void variasSementesSempreGarantemContrasteDeUI() {
        for (String seed : new String[] { "#3F8CFF", "#7B61FF", "#E0524D", "#F0A824", "#12B76A", "#111111" }) {
            double c = contrasteComBranco(service.derivar(seed).brand());
            assertTrue(c >= 3.0, "branco sobre a marca de " + seed + " deve ter >= 3:1 (foi " + c + ")");
        }
    }

    private static void assertHex(String cor) {
        assertTrue(cor != null && cor.matches("^#[0-9A-F]{6}$"), "cor hex válida: " + cor);
    }

    private static double contrasteComBranco(String hex) {
        return 1.05 / (luminancia(hex) + 0.05);
    }

    private static double luminancia(String hex) {
        int[] rgb = { Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16) };
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double x = rgb[i] / 255.0;
            c[i] = x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }
}
