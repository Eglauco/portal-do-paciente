package com.example.pop.storage;

/**
 * Qual S3 usar para um objeto. O ESCOPO é codificado nos primeiros segmentos da KEY do objeto para que:
 * <ul>
 *   <li>a leitura/exclusão descubra o escopo só pela URL salva (sem o chamador repassar a unidade);</li>
 *   <li>UM único bucket possa servir VÁRIOS inquilinos e unidades sem colisão (o schema separa os
 *       inquilinos; o id separa as unidades dentro do inquilino).</li>
 * </ul>
 * Estrutura da key:
 * <ul>
 *   <li>{@code inquilino-<schema>/unidade-<id>/<pasta>/…} — S3 da UNIDADE;</li>
 *   <li>{@code inquilino-<schema>/<pasta>/…} — S3 PADRÃO do inquilino (fotos, modelos de TCLE, logomarca);</li>
 *   <li>{@code plat/<pasta>/…} — S3 da PLATAFORMA (central/cross-tenant; branding do super-admin).</li>
 * </ul>
 */
public record EscopoS3(Tipo tipo, Long unidadeId) {

    public enum Tipo { UNIDADE, INQUILINO_PADRAO, PLATAFORMA }

    public static EscopoS3 unidade(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("unidadeId é obrigatório para o escopo de unidade.");
        }
        return new EscopoS3(Tipo.UNIDADE, id);
    }

    public static EscopoS3 inquilinoPadrao() {
        return new EscopoS3(Tipo.INQUILINO_PADRAO, null);
    }

    public static EscopoS3 plataforma() {
        return new EscopoS3(Tipo.PLATAFORMA, null);
    }

    /** Prefixo gravado na KEY do objeto (depende do schema, para 1 bucket servir vários inquilinos). */
    public String prefixoChave(String schema) {
        return switch (tipo) {
            case UNIDADE -> pastaInquilino(schema) + "/unidade-" + unidadeId;
            case INQUILINO_PADRAO -> pastaInquilino(schema);
            case PLATAFORMA -> "plat";
        };
    }

    /** Id estável (schema-independente) para a chave do cache de client/presigner em memória. */
    public String cacheId() {
        return switch (tipo) {
            case UNIDADE -> "u" + unidadeId;
            case INQUILINO_PADRAO -> "inq";
            case PLATAFORMA -> "plat";
        };
    }

    /**
     * Resolve o escopo a partir da KEY do objeto (1º/2º segmentos). Retorna {@code null} para key não
     * reconhecida (ex.: objetos legados sem este prefixo) — eles deixam de resolver (break limpo).
     */
    public static EscopoS3 doChave(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        int i = key.indexOf('/');
        String seg0 = i < 0 ? key : key.substring(0, i);
        if (seg0.equals("plat")) {
            return plataforma();
        }
        if (seg0.startsWith("inquilino-")) {
            String resto = i < 0 ? "" : key.substring(i + 1);
            int j = resto.indexOf('/');
            String seg1 = j < 0 ? resto : resto.substring(0, j);
            if (seg1.startsWith("unidade-")) {
                try {
                    return unidade(Long.parseLong(seg1.substring("unidade-".length())));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return inquilinoPadrao();
        }
        return null;
    }

    /** Pasta do inquilino: {@code inquilino-<schema>} com o schema sanitizado (seguro para key de S3). */
    private static String pastaInquilino(String schema) {
        String s = schema == null ? "" : schema.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "");
        return "inquilino-" + (s.isEmpty() ? "x" : s);
    }
}
