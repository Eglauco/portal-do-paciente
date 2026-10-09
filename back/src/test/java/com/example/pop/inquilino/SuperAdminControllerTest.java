package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;

/**
 * Cadastro de inquilino pelo super-admin (Fase 1): sem o segredo → 401; com o segredo → cria o
 * registro no public E provisiona o schema (baseline limpo).
 */
@SpringBootTest
class SuperAdminControllerTest {

    private static final String SCHEMA = "inq_teste_superadmin";
    /** Default de dev de app.superadmin.secret (SUPERADMIN_SECRET não definido no teste). */
    private static final String SEGREDO = "dev-superadmin-trocar-em-producao";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private Filter springSecurityFilterChain;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private InquilinoRepository inquilinoRepository;

    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        limpar();
    }

    @AfterEach
    void tearDown() throws Exception {
        limpar();
    }

    @Test
    void semSegredoRetorna401() throws Exception {
        String corpo = "{\"nome\":\"Clinica X\",\"schemaName\":\"" + SCHEMA + "\"}";
        mvc.perform(post("/superadmin/inquilinos")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void comSegredoCriaInquilinoEProvisionaSchema() throws Exception {
        String corpo = "{\"nome\":\"Clinica Teste\",\"schemaName\":\"" + SCHEMA + "\"}";
        mvc.perform(post("/superadmin/inquilinos").header("X-SuperAdmin-Secret", SEGREDO)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        assertTrue(inquilinoRepository.findBySchemaName(SCHEMA).isPresent(),
                "o inquilino deve ter sido registrado no public");
        assertTrue(schemaExiste(SCHEMA), "o schema do inquilino deve ter sido provisionado");
    }

    private boolean schemaExiste(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection
                        .prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void limpar() throws Exception {
        inquilinoRepository.findBySchemaName(SCHEMA).ifPresent(inquilinoRepository::delete);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + SCHEMA + "\" CASCADE");
        }
    }
}
