package com.example.pop.storage;

/**
 * Qual S3 usar para um objeto. É codificado no 1º segmento da KEY do objeto (prefixo) para que a
 * leitura/exclusão descubra o escopo só pela URL salva, sem o chamador precisar repassar a unidade:
 * <ul>
 *   <li>{@code u<id>/…} — S3 da UNIDADE de id {@code <id>};</li>
 *   <li>{@code inq/…} — S3 PADRÃO do inquilino (fotos, modelos de TCLE, logomarca);</li>
 *   <li>{@code plat/…} — S3 da PLATAFORMA (global do application.properties; branding do super-admin).</li>
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

    /** Prefixo gravado como 1º segmento da key do objeto. */
    public String prefixo() {
        return switch (tipo) {
            case UNIDADE -> "u" + unidadeId;
            case INQUILINO_PADRAO -> "inq";
            case PLATAFORMA -> "plat";
        };
    }

    /**
     * Resolve o escopo a partir do 1º segmento da key (ex.: "u123", "inq", "plat").
     * Retorna {@code null} para prefixo não reconhecido (ex.: objetos legados sem prefixo de escopo).
     */
    public static EscopoS3 doPrefixo(String seg) {
        if (seg == null || seg.isBlank()) {
            return null;
        }
        if (seg.equals("inq")) {
            return inquilinoPadrao();
        }
        if (seg.equals("plat")) {
            return plataforma();
        }
        if (seg.length() > 1 && seg.charAt(0) == 'u') {
            try {
                return unidade(Long.parseLong(seg.substring(1)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
