package com.example.pop.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.inquilino.ProvisionamentoService;
import com.example.pop.tenant.TenantContext;

/**
 * Cache de config POR INQUILINO (Fase 2 #1): a config é por-tenant (cada schema tem a sua, inclusive os
 * SEGREDOs de provedores). O cache do {@link ConfiguracaoService} não pode ser único/global — senão a
 * config do 1º inquilino a carregar vaza para os demais. Este teste prova que um valor alterado no schema
 * de um inquilino NÃO é enxergado ao ler no {@code public} (com o cache antigo, único, este teste falharia).
 */
@SpringBootTest
class ConfiguracaoCachePorInquilinoTest {

    private static final String SCHEMA = "inq_teste_cfgcache";
    private static final String CHAVE = ChaveConfiguracao.NOME_PLATAFORMA; // TEXTO
    private static final String VALOR_TENANT = "SO_DESTE_INQUILINO";

    @Autowired
    private ConfiguracaoService service;
    @Autowired
    private ConfiguracaoRepository repository;
    @Autowired
    private ProvisionamentoService provisionamentoService;

    @BeforeEach
    void setup() {
        provisionamentoService.dropSchema(SCHEMA);
        provisionamentoService.provisionarSchema(SCHEMA); // cria o schema + seeds (configuracao incluída)
    }

    @AfterEach
    void tearDown() {
        provisionamentoService.dropSchema(SCHEMA);
    }

    @Test
    void configNaoVazaEntreInquilinos() {
        String anterior = TenantContext.atualBruto();
        try {
            // 1) Valor atual do public (inquilino padrão), com o cache do public limpo.
            TenantContext.limpar();
            service.invalidarCache();
            String valorPublic = service.lerTexto(CHAVE);
            assertNotEquals(VALOR_TENANT, valorPublic, "pré-condição: o public não tem o valor de teste");

            // 2) Altera NOME_PLATAFORMA SOMENTE no schema do inquilino de teste.
            TenantContext.definir(SCHEMA);
            Configuracao c = repository.findByChave(CHAVE).orElseThrow();
            c.setValorTexto(VALOR_TENANT);
            repository.save(c);
            service.invalidarCache(); // invalida só o cache do inquilino atual (SCHEMA)
            assertEquals(VALOR_TENANT, service.lerTexto(CHAVE), "o inquilino lê o próprio valor");

            // 3) De volta ao public: NÃO pode enxergar o valor do inquilino (senão o cache vazou).
            TenantContext.limpar();
            assertEquals(valorPublic, service.lerTexto(CHAVE),
                    "config do inquilino VAZOU para o public (cache compartilhado entre inquilinos)");
            assertNotEquals(VALOR_TENANT, service.lerTexto(CHAVE));
        } finally {
            // Remove a entrada de cache do schema de teste (evita órfã após o drop) e restaura o contexto.
            TenantContext.definir(SCHEMA);
            service.invalidarCache();
            if (anterior != null) {
                TenantContext.definir(anterior);
            } else {
                TenantContext.limpar();
            }
        }
    }
}
