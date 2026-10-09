package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.tenant.TenantContext;

/**
 * Execução por inquilino (Fase 2 #3): os jobs @Scheduled rodam sem request (sem inquilino). O
 * {@link ExecucaoPorInquilino} deve rodar a ação uma vez por inquilino ATIVO, com o {@link TenantContext}
 * fixado no schema de cada um, PULANDO o inquilino inativo e o padrão (public = plataforma).
 */
@SpringBootTest
class ExecucaoPorInquilinoTest {

    private static final String SCHEMA_ATIVO = "inq_teste_exec_ativo";
    private static final String SCHEMA_INATIVO = "inq_teste_exec_inativo";

    @Autowired
    private ExecucaoPorInquilino execucaoPorInquilino;
    @Autowired
    private InquilinoRepository repository;

    @BeforeEach
    void setup() {
        limpar();
        repository.save(inquilino("Ativo", SCHEMA_ATIVO, "ATIVO"));
        repository.save(inquilino("Inativo", SCHEMA_INATIVO, "INATIVO"));
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void rodaSoNosAtivosForaDoPublicComOContextoCerto() {
        TenantContext.limpar(); // estado inicial conhecido (público) para a asserção de restauração
        // schema do inquilino -> TenantContext.atual() visto DENTRO da ação
        Map<String, String> contextoPorSchema = new ConcurrentHashMap<>();
        execucaoPorInquilino.paraCadaInquilinoAtivo(schema -> contextoPorSchema.put(schema, TenantContext.atual()));

        assertTrue(contextoPorSchema.containsKey(SCHEMA_ATIVO), "roda no inquilino ATIVO");
        assertEquals(SCHEMA_ATIVO, contextoPorSchema.get(SCHEMA_ATIVO),
                "o TenantContext foi fixado no schema do inquilino");
        assertFalse(contextoPorSchema.containsKey(SCHEMA_INATIVO), "NÃO roda em inquilino inativo");
        assertFalse(contextoPorSchema.containsKey(TenantContext.SCHEMA_PADRAO),
                "NÃO roda no schema de plataforma (public)");

        // O contexto da thread foi restaurado após a execução.
        assertEquals(TenantContext.SCHEMA_PADRAO, TenantContext.atual(), "contexto restaurado ao fim");
    }

    private static Inquilino inquilino(String nome, String schema, String situacao) {
        Inquilino i = new Inquilino();
        i.setNome(nome);
        i.setSchemaName(schema);
        i.setSituacao(situacao);
        return i;
    }

    private void limpar() {
        repository.findBySchemaName(SCHEMA_ATIVO).ifPresent(repository::delete);
        repository.findBySchemaName(SCHEMA_INATIVO).ifPresent(repository::delete);
    }
}
