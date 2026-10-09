package com.example.pop.tenant;

/**
 * Contexto do inquilino (multi-tenant por SCHEMA) da thread atual.
 *
 * <p>O {@link TenantIdentifierResolver} lê daqui qual schema o Hibernate deve usar, e o
 * {@link SchemaMultiTenantConnectionProvider} aplica o {@code SET search_path} na conexão.
 * Quando nada é definido, cai no {@link #SCHEMA_PADRAO} ({@code public}) — é o que mantém o
 * comportamento single-tenant atual enquanto a resolução por request (pelo claim do JWT) ainda
 * não foi ligada (Fase 0.2+).
 *
 * <p>É um {@link ThreadLocal}: só vale dentro da thread que o definiu. Threads de {@code @Async},
 * do scheduler e do WebSocket NÃO herdam este valor — elas precisarão setá-lo explicitamente
 * (Fase 2). Sempre limpar no {@code finally} da unidade de trabalho para não vazar para o próximo
 * uso da mesma thread do pool.
 */
public final class TenantContext {

    /** Schema usado quando nenhum inquilino foi resolvido (comportamento single-tenant atual). */
    public static final String SCHEMA_PADRAO = "public";

    private static final ThreadLocal<String> ATUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Define o schema do inquilino da thread atual. */
    public static void definir(String schema) {
        ATUAL.set(schema);
    }

    /** Schema do inquilino da thread atual, ou {@link #SCHEMA_PADRAO} se nenhum foi definido. */
    public static String atual() {
        String schema = ATUAL.get();
        return schema != null ? schema : SCHEMA_PADRAO;
    }

    /** Remove o schema da thread atual (chamar SEMPRE no finally para não vazar entre requests). */
    public static void limpar() {
        ATUAL.remove();
    }
}
