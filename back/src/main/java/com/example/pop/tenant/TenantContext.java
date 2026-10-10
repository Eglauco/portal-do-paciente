package com.example.pop.tenant;

/**
 * Contexto do inquilino (multi-tenant por SCHEMA) da thread atual.
 *
 * <p>O {@link TenantIdentifierResolver} lê daqui qual schema o Hibernate deve usar, e o
 * {@link SchemaMultiTenantConnectionProvider} aplica o {@code SET search_path} na conexão.
 * Quando nada é definido, cai no {@link #SCHEMA_PADRAO}.
 *
 * <p>Design B: o schema PADRÃO é um schema NOMEADO ({@code principal}), NÃO o {@code public}. O
 * {@code public} guarda só as tabelas de PLATAFORMA (inquilino/usuario_login/paciente_login/
 * assinatura_roteamento, todas {@code @Table(schema="public")}); as tabelas de DOMÍNIO vivem em cada
 * schema de inquilino e no {@code principal} (o inquilino padrão/fallback, também usado pelo boot do
 * Hibernate — validate — e pelos testes, que rodam sem inquilino resolvido).
 *
 * <p>É um {@link ThreadLocal}: só vale dentro da thread que o definiu. Threads de {@code @Async},
 * do scheduler e do WebSocket NÃO herdam este valor — elas setam-no explicitamente (Fase 2). Sempre
 * limpar no {@code finally} da unidade de trabalho para não vazar para o próximo uso da thread do pool.
 */
public final class TenantContext {

    /**
     * Schema do inquilino PADRÃO (fallback quando nenhum inquilino foi resolvido). É um schema NOMEADO
     * com as tabelas de domínio — NÃO o {@code public} (que é só plataforma).
     */
    public static final String SCHEMA_PADRAO = "principal";

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

    /** Valor cru do ThreadLocal (pode ser {@code null} se nada foi definido) — para salvar/restaurar. */
    public static String atualBruto() {
        return ATUAL.get();
    }

    /** Remove o schema da thread atual (chamar SEMPRE no finally para não vazar entre requests). */
    public static void limpar() {
        ATUAL.remove();
    }
}
