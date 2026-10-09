package com.example.pop.inquilino;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

/**
 * Provisiona o schema de um inquilino: cria o schema e aplica as migrations nele (via Flyway
 * programático sobre o MESMO datasource — a conexão volta ao pool e o {@code search_path} é
 * re-setado pelo {@code SchemaMultiTenantConnectionProvider} em cada uso do Hibernate).
 *
 * <p>Embrião do motor de provisionamento da Fase 1 — que ainda vai: separar um BASELINE limpo dos
 * seeds de demo (hoje roda TODAS as migrations, inclusive os inserts de demonstração), semear o 1º
 * admin + o registro no public, e ganhar atomicidade/rollback e tratamento de concorrência. Por ora
 * é suficiente para a PROVA de isolamento (Fase 0.4).
 */
@Service
public class ProvisionamentoService {

    /** Identificador de schema seguro (Postgres: minúsculo, começa por letra/underscore, ≤ 63). */
    private static final Pattern SCHEMA_VALIDO = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private final DataSource dataSource;

    public ProvisionamentoService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Cria o schema (se não existir) e aplica todas as migrations nele. */
    public void provisionarSchema(String schema) {
        String nome = validar(schema);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + nome + "\"");
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar o schema do inquilino: " + nome, e);
        }
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(nome)
                .defaultSchema(nome)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    private static String validar(String schema) {
        if (schema == null || "public".equals(schema) || !SCHEMA_VALIDO.matcher(schema).matches()) {
            throw new IllegalArgumentException("Nome de schema de inquilino inválido: " + schema);
        }
        return schema;
    }
}
