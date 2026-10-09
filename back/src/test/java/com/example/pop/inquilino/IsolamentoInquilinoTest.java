package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.configuracao.Configuracao;
import com.example.pop.configuracao.ConfiguracaoRepository;
import com.example.pop.configuracao.TipoConfiguracao;
import com.example.pop.tenant.TenantContext;

/**
 * Prova de isolamento (Fase 0.4): provisiona um 2º schema de inquilino DE VERDADE (CREATE SCHEMA +
 * todas as migrations) e verifica que um marcador escrito nele NÃO aparece no schema {@code public}.
 * Exercita o {@code SchemaMultiTenantConnectionProvider} + {@code TenantContext} ponta a ponta na
 * camada de dados — se o search_path não isolasse, o marcador vazaria.
 */
@SpringBootTest
class IsolamentoInquilinoTest {

    private static final String SCHEMA = "inq_teste_0_4";
    private static final String CHAVE = "ISOLAMENTO_TESTE_0_4";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private ProvisionamentoService provisionamentoService;
    @Autowired
    private InquilinoRepository inquilinoRepository;
    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    @BeforeEach
    void setup() throws Exception {
        limpar();
        provisionamentoService.provisionarSchema(SCHEMA);
        Inquilino inquilino = new Inquilino();
        inquilino.setNome("Teste Isolamento 0.4");
        inquilino.setSchemaName(SCHEMA);
        inquilinoRepository.save(inquilino); // sem tenant setado -> grava no public.inquilino
    }

    @AfterEach
    void tearDown() throws Exception {
        limpar();
    }

    @Test
    void inquilinoSoEnxergaOProprioSchema() {
        // Escreve um marcador SÓ no schema do inquilino de teste.
        TenantContext.definir(SCHEMA);
        try {
            Configuracao marcador = new Configuracao();
            marcador.setNome("Marcador de isolamento");
            marcador.setChave(CHAVE);
            marcador.setTipoConfiguracao(TipoConfiguracao.TEXTO);
            marcador.setValorTexto("so-no-inq-teste");
            configuracaoRepository.save(marcador);
            // No MESMO schema, o marcador existe.
            assertTrue(configuracaoRepository.findByChave(CHAVE).isPresent(),
                    "o marcador deve existir no schema do inquilino que o criou");
        } finally {
            TenantContext.limpar();
        }

        // Sem tenant (schema public): o marcador do inquilino de teste NÃO pode vazar.
        assertTrue(configuracaoRepository.findByChave(CHAVE).isEmpty(),
                "o marcador do inquilino de teste NAO pode aparecer no public (isolamento provado)");
    }

    /** Remove o registro no public + dropa o schema de teste (idempotente). */
    private void limpar() throws Exception {
        inquilinoRepository.findBySchemaName(SCHEMA).ifPresent(inquilinoRepository::delete);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + SCHEMA + "\" CASCADE");
        }
    }
}
