package com.example.pop.inquilino;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /**
     * Tabelas de REFERÊNCIA/CONFIG que um inquilino novo precisa ter populadas (seed legítimo das
     * migrations). Todas as OUTRAS são truncadas no baseline limpo (dados de demo/transacionais).
     * {@code flyway_schema_history} nunca é tocada.
     */
    private static final Set<String> MANTER_NO_BASELINE = Set.of(
            "flyway_schema_history", "perfil", "perfil_tela", "configuracao",
            "conselho", "motivo_falta", "categoria_nps", "tipo_manifestacao");

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

    /**
     * Provisiona um inquilino com BASELINE LIMPO: cria o schema + migrations e em seguida TRUNCA os
     * dados de demo/transacionais, mantendo só as tabelas de referência/config ({@link
     * #MANTER_NO_BASELINE}). Resultado: schema completo, com perfil Administrador + configs +
     * referências, mas SEM os usuários/unidades/pacientes/agendamentos fictícios das migrations.
     */
    public void provisionarInquilino(String schema) {
        provisionarSchema(schema);
        limparDadosDemo(validar(schema));
    }

    /**
     * Apaga os dados de demo/transacionais do schema, mantendo só as tabelas de referência/config.
     * Usa {@code session_replication_role = replica} para DESLIGAR a verificação de FK na sessão —
     * assim o DELETE roda em qualquer ordem e NÃO cascateia nas tabelas mantidas (ex.: a
     * {@code configuracao}, que tem FK {@code atualizado_por → usuario}, ficaria vazia num
     * TRUNCATE CASCADE). Requer superusuário (dev/Railway usam {@code postgres}).
     */
    private void limparDadosDemo(String schema) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            List<String> aLimpar = new ArrayList<>();
            try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), schema, "%",
                    new String[] { "TABLE" })) {
                while (rs.next()) {
                    String tabela = rs.getString("TABLE_NAME");
                    if (!MANTER_NO_BASELINE.contains(tabela)) {
                        aLimpar.add("\"" + schema + "\".\"" + tabela + "\"");
                    }
                }
            }
            statement.execute("SET session_replication_role = 'replica'");
            try {
                for (String tabela : aLimpar) {
                    statement.execute("DELETE FROM " + tabela);
                }
            } finally {
                statement.execute("SET session_replication_role = 'origin'");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao limpar os dados de demo do schema " + schema, e);
        }
    }

    private static String validar(String schema) {
        if (schema == null || "public".equals(schema) || !SCHEMA_VALIDO.matcher(schema).matches()) {
            throw new IllegalArgumentException("Nome de schema de inquilino inválido: " + schema);
        }
        return schema;
    }
}
