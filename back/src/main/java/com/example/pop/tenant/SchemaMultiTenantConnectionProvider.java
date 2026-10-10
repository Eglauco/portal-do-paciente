package com.example.pop.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

/**
 * Multi-tenancy por SCHEMA sobre UM único {@link DataSource} (pool compartilhado): cada conexão
 * entregue ao Hibernate tem seu {@code search_path} apontado para o schema do inquilino, e é
 * RESETADO para {@code public} antes de voltar ao pool.
 *
 * <p>O reset no release é CRÍTICO: sem ele, a próxima requisição que pegar esta conexão herdaria o
 * schema do inquilino anterior — vazamento de dados clínicos entre clientes (o pior bug possível
 * num sistema de saúde). Ver o audit multi-inquilino.
 *
 * <p>Fase 0.1: enquanto ninguém define o {@link TenantContext}, o tenant resolvido é
 * {@code public} e o {@code search_path} fica {@code "public", public} — ou seja, comportamento
 * idêntico ao single-tenant atual.
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    /** Identificador de schema seguro (Postgres: minúsculo, começa por letra/underscore, ≤ 63). */
    private static final Pattern SCHEMA_VALIDO = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private final transient DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        // Usada pelo bootstrap/validate do Hibernate (sem tenant): schema padrão.
        Connection connection = dataSource.getConnection();
        resetarSchema(connection);
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        resetarSchema(connection);
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        definirSchema(connection, tenantIdentifier);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        // Reseta ANTES de devolver ao pool, independentemente do tenant.
        resetarSchema(connection);
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    /** Aponta o search_path para o schema do inquilino, com {@code public} como fallback (roteamento). */
    private void definirSchema(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + identificadorSeguro(schema) + ", public");
        }
    }

    /**
     * Volta o search_path ao schema PADRÃO (schema nomeado com o domínio + {@code public} como fallback
     * para as tabelas de plataforma). Usado no release (anti-leak) e no bootstrap/validate do Hibernate.
     */
    private void resetarSchema(Connection connection) throws SQLException {
        definirSchema(connection, TenantContext.SCHEMA_PADRAO);
    }

    /**
     * Valida e cita o nome do schema para interpolar no {@code SET search_path} (que não aceita bind
     * parameter). Barra injeção: só aceita identificador Postgres minúsculo.
     */
    private static String identificadorSeguro(String schema) {
        if (schema == null || !SCHEMA_VALIDO.matcher(schema).matches()) {
            throw new IllegalArgumentException("Nome de schema de inquilino inválido: " + schema);
        }
        return "\"" + schema + "\"";
    }
}
