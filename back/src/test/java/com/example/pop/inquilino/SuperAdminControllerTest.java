package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * registro no public, provisiona o schema (baseline limpo) e SEMEIA a 1ª unidade + o 1º admin
 * (no tenant) + o ponteiro de roteamento usuario_login (no public).
 */
@SpringBootTest
class SuperAdminControllerTest {

    private static final String SCHEMA = "inq_teste_superadmin";
    private static final String ADMIN_EMAIL = "admin.teste.superadmin@clinica.com";
    /** Default de dev de app.superadmin.secret (SUPERADMIN_SECRET não definido no teste). */
    private static final String SEGREDO = "dev-superadmin-trocar-em-producao";
    private static final String CORPO = "{\"nome\":\"Clinica Teste\",\"schemaName\":\"" + SCHEMA + "\","
            + "\"unidadeNome\":\"Unidade Central\",\"adminNome\":\"Admin Teste\","
            + "\"adminEmail\":\"" + ADMIN_EMAIL + "\",\"adminSenha\":\"senha-admin-123\"}";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private Filter springSecurityFilterChain;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private InquilinoRepository inquilinoRepository;
    @Autowired
    private UsuarioLoginRepository usuarioLoginRepository;

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
        mvc.perform(post("/superadmin/inquilinos")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void comSegredoCriaProvisionaESemeia() throws Exception {
        mvc.perform(post("/superadmin/inquilinos").header("X-SuperAdmin-Secret", SEGREDO)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated());

        assertTrue(inquilinoRepository.findBySchemaName(SCHEMA).isPresent(), "inquilino registrado no public");
        assertTrue(schemaExiste(SCHEMA), "schema do inquilino provisionado");
        assertTrue(usuarioLoginRepository.findByEmailIgnoreCase(ADMIN_EMAIL).isPresent(),
                "ponteiro de roteamento (usuario_login) criado no public");
        assertEquals(1, contarUsuarioNoTenant(ADMIN_EMAIL), "o admin foi semeado no schema do inquilino");
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

    private long contarUsuarioNoTenant(String email) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT count(*) FROM \"" + SCHEMA + "\".usuario WHERE lower(email) = lower(?)")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void limpar() throws Exception {
        usuarioLoginRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(usuarioLoginRepository::delete);
        inquilinoRepository.findBySchemaName(SCHEMA).ifPresent(inquilinoRepository::delete);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + SCHEMA + "\" CASCADE");
        }
    }
}
