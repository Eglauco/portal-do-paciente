package com.example.pop.tema;

import org.springframework.stereotype.Service;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;

/**
 * Deriva a paleta do tema a partir de UMA cor semente (a configuração
 * {@code COR_PRIMARIA_PLATAFORMA}). A derivação acontece no servidor para o front e o app
 * consumirem tokens IDÊNTICOS. Estratégia:
 * <ul>
 *   <li><b>Clamp</b> da semente para uma faixa usável (nem clara nem escura demais), de modo
 *       que texto branco por cima seja legível (WCAG AA para UI/texto grande) — nunca reprova.</li>
 *   <li>Tons escuros (deep/pine) e claro (glow) por razão de luminosidade, calibrados para
 *       reproduzir a paleta atual quando a semente é o verde padrão.</li>
 *   <li>{@code onBrand} = branco (o clamp garante contraste) ou escuro por luminância.</li>
 * </ul>
 * As cores semânticas (sucesso/erro/aviso) e a paleta de gráficos NÃO passam por aqui —
 * ficam fixas no front/app de propósito.
 */
@Service
public class TemaService {

    /** Verde atual — semente padrão e fail-safe. */
    static final String COR_PADRAO = "#0E8C7F";

    private final ConfiguracaoService configuracaoService;

    public TemaService(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    /** Paleta da cor primária configurada; cai no verde padrão se a config faltar/quebrar. */
    public PaletaTema tema() {
        String semente;
        try {
            semente = configuracaoService.lerCor(ChaveConfiguracao.COR_PRIMARIA_PLATAFORMA);
        } catch (RuntimeException e) {
            semente = COR_PADRAO;
        }
        return derivar(semente == null || semente.isBlank() ? COR_PADRAO : semente);
    }

    /** Deriva a paleta a partir de uma cor {@code #RRGGBB} (inválida → verde padrão). */
    public PaletaTema derivar(String hex) {
        int[] original = hexParaRgb(hex);
        String sementeHex = original != null ? normalizar(hex) : COR_PADRAO;
        if (original == null) {
            original = hexParaRgb(COR_PADRAO);
        }
        float[] hsl = rgbParaHsl(original);
        float h = hsl[0];
        // Preserva a saturação da semente (só a luminosidade é ajustada p/ contraste). Assim uma
        // cor acromática (cinza/branco/preto, hue indefinido) vira um tema CINZA — não avermelhado.
        float s = hsl[1];
        float lClamp = clamp(hsl[2], 0.20f, 0.55f);
        // Escurece até o branco por cima ficar legível (UI/botões): luminância <= 0.21.
        float l = escurecerAte(h, s, lClamp, 0.21, 0.18f);

        // Semente já na faixa usável → preserva a marca exatamente; senão, usa o valor clampado.
        boolean intacta = s == hsl[1] && l == hsl[2];
        int[] rgb = intacta ? original : hslParaRgb(h, s, l);
        String brand = intacta ? sementeHex : hexDe(rgb);

        String brandDeep = hslParaHex(h, s, l * 0.68f);
        String brandPine = hslParaHex(h, s, l * 0.34f);
        String glow = hslParaHex(h, Math.min(s, 0.62f), l + (1 - l) * 0.55f);
        String onBrand = contrasteBrancoOk(rgb) ? "#FFFFFF" : "#0C1F1C";
        String brandRgb = rgb[0] + ", " + rgb[1] + ", " + rgb[2];
        // Fundo: tom bem claro (levemente tingido pela marca) — mantém painéis brancos legíveis.
        String bg = hslParaHex(h, Math.min(s, 0.16f), 0.965f);
        return new PaletaTema(brand, brandDeep, brandPine, glow, onBrand, brandRgb, bg);
    }

    // ---------- utilidades de cor ----------

    /** Converte um RGB 0..255 para [h(0..360), s(0..1), l(0..1)]. */
    private static float[] rgbParaHsl(int[] rgbInt) {
        float r = rgbInt[0] / 255f, g = rgbInt[1] / 255f, b = rgbInt[2] / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        if (max == min) {
            return new float[] { 0f, 0f, l };
        }
        float d = max - min;
        float s = l > 0.5f ? d / (2 - max - min) : d / (max + min);
        float hh;
        if (max == r) {
            hh = (g - b) / d + (g < b ? 6 : 0);
        } else if (max == g) {
            hh = (b - r) / d + 2;
        } else {
            hh = (r - g) / d + 4;
        }
        return new float[] { hh * 60f, s, l };
    }

    private static int[] hexParaRgb(String hex) {
        if (hex == null || !hex.trim().matches("^#[0-9A-Fa-f]{6}$")) {
            return null;
        }
        String h = hex.trim();
        return new int[] {
                Integer.parseInt(h.substring(1, 3), 16),
                Integer.parseInt(h.substring(3, 5), 16),
                Integer.parseInt(h.substring(5, 7), 16) };
    }

    private static int[] hslParaRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            float hk = ((h % 360f) + 360f) % 360f / 360f;
            r = hue2rgb(p, q, hk + 1f / 3f);
            g = hue2rgb(p, q, hk);
            b = hue2rgb(p, q, hk - 1f / 3f);
        }
        return new int[] { Math.round(r * 255), Math.round(g * 255), Math.round(b * 255) };
    }

    private static float hue2rgb(float p, float q, float t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1f / 6f) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1f / 2f) {
            return q;
        }
        if (t < 2f / 3f) {
            return p + (q - p) * (2f / 3f - t) * 6;
        }
        return p;
    }

    private static String hslParaHex(float h, float s, float l) {
        return hexDe(hslParaRgb(h, clamp(s, 0f, 1f), clamp(l, 0f, 1f)));
    }

    private static String hexDe(int[] rgb) {
        return String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }

    private static String normalizar(String hex) {
        return hex.trim().toUpperCase();
    }

    /** Luminância relativa (WCAG) de um RGB 0..255. */
    private static double luminancia(int[] rgb) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double x = rgb[i] / 255.0;
            c[i] = x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    /** Reduz a luminosidade até a luminância ficar <= alvo (ou atingir o piso). */
    private static float escurecerAte(float h, float s, float l, double alvoLumMax, float piso) {
        float ll = l;
        while (ll > piso && luminancia(hslParaRgb(h, s, ll)) > alvoLumMax) {
            ll -= 0.01f;
        }
        return ll;
    }

    /** true se texto BRANCO sobre a cor atinge ~3:1 (contraste de UI/texto grande). */
    private static boolean contrasteBrancoOk(int[] rgb) {
        return 1.05 / (luminancia(rgb) + 0.05) >= 3.0;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
