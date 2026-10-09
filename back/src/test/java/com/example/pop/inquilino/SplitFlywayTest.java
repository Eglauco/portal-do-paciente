package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Split das migrations PLATAFORMA (db/platform: inquilino/usuario_login/paciente_login, só no public)
 * × TENANT (db/migration: o domínio por-inquilino). Prova que ao provisionar um inquilino novo:
 * <ul>
 *   <li>o schema do inquilino recebe o domínio de tenant, mas NÃO as tabelas de plataforma;</li>
 *   <li>o {@code flyway_schema_history} do inquilino NÃO registra V148/149/150 (fica limpo);</li>
 *   <li>o public (plataforma + inquilino padrão) tem as três.</li>
 * </ul>
 * Como é um {@code @SpringBootTest}, o boot do contexto também valida que o Flyway do app principal
 * continua íntegro no public com as três migrations movidas para {@code db/platform}.
 */
@SpringBootTest
class SplitFlywayTest {

    private static final String SCHEMA = "inq_teste_split_flyway";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private ProvisionamentoService provisionamentoService;

    @BeforeEach
    void setup() throws Exception {
        dropSchema();
        provisionamentoService.provisionarSchema(SCHEMA);
    }

    @AfterEach
    void tearDown() throws Exception {
        dropSchema();
    }

    @Test
    void plataformaFicaSoNoPublicETenantTemSoODominio() throws Exception {
        // Tabelas de plataforma: só no public, nunca no schema do inquilino.
        assertTrue(tabelaExiste("public", "inquilino"), "plataforma existe no public");
        assertFalse(tabelaExiste(SCHEMA, "inquilino"), "inquilino é de plataforma — fora do schema do inquilino");
        assertFalse(tabelaExiste(SCHEMA, "usuario_login"), "usuario_login é de plataforma");
        assertFalse(tabelaExiste(SCHEMA, "paciente_login"), "paciente_login é de plataforma");
        // Domínio de tenant: provisionado no schema do inquilino.
        assertTrue(tabelaExiste(SCHEMA, "paciente"), "o domínio de tenant foi provisionado");

        // Histórico do Flyway: o inquilino não registra as de plataforma; o public registra as três.
        assertEquals(0, versoesPlataforma(SCHEMA), "tenant não aplica V148/149/150");
        assertEquals(3, versoesPlataforma("public"), "public aplica V148/149/150");
    }

    /** Quantas das migrations de plataforma (148/149/150) estão no flyway_schema_history do schema. */
    private long versoesPlataforma(String schema) throws Exception {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM \"" + schema
                        + "\".flyway_schema_history WHERE version IN ('148', '149', '150')")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private boolean tabelaExiste(String schema, String tabela) throws Exception {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, tabela);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void dropSchema() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS \"" + SCHEMA + "\" CASCADE");
        }
    }
}
