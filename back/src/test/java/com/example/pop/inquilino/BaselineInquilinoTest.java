package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prova o BASELINE LIMPO (Fase 1): {@code provisionarInquilino} cria um schema completo mas SEM os
 * dados de demonstração — só com as tabelas de referência/config populadas. Verifica tabela a tabela:
 * demo/transacional = VAZIO; referência/config = PRESENTE.
 */
@SpringBootTest
class BaselineInquilinoTest {

    private static final String SCHEMA = "inq_teste_baseline";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private ProvisionamentoService provisionamentoService;

    @BeforeEach
    void setup() throws Exception {
        dropar();
        provisionamentoService.provisionarInquilino(SCHEMA);
    }

    @AfterEach
    void tearDown() throws Exception {
        dropar();
    }

    @Test
    void baselineTemReferenciaMasNaoTemDemo() throws Exception {
        // Demo / transacional: devem estar VAZIAS.
        assertEquals(0, contar("usuario"), "usuario (demo) deve estar vazio");
        assertEquals(0, contar("unidade"), "unidade (demo) deve estar vazia");
        assertEquals(0, contar("paciente"), "paciente (demo) deve estar vazio");
        assertEquals(0, contar("agenda"), "agenda (demo) deve estar vazia");
        assertEquals(0, contar("horario"), "horario (demo) deve estar vazio");
        assertEquals(0, contar("chat"), "chat (demo) deve estar vazio");
        assertEquals(0, contar("prontuario"), "prontuario (demo) deve estar vazio");

        // Referência / config: devem estar POPULADAS.
        assertTrue(contar("perfil") > 0, "deve ter o perfil Administrador");
        assertTrue(contar("perfil_tela") > 0, "deve ter as telas do perfil Administrador");
        assertTrue(contar("configuracao") > 0, "deve ter as configuracoes padrao");
        assertTrue(contar("conselho") > 0, "deve ter os conselhos de classe");
        assertTrue(contar("motivo_falta") > 0, "deve ter os motivos de falta");
        assertTrue(contar("categoria_nps") > 0, "deve ter as categorias de NPS");
        assertTrue(contar("tipo_manifestacao") > 0, "deve ter os tipos de manifestacao");
    }

    private long contar(String tabela) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT count(*) FROM \"" + SCHEMA + "\".\"" + tabela + "\"")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void dropar() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + SCHEMA + "\" CASCADE");
        }
    }
}
