package com.example.pop.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.tenant.TenantContext;

/**
 * Propagação do inquilino para as threads @Async (Fase 2 #2): o {@link TenantContext} é um ThreadLocal e
 * NÃO é herdado pelas threads do pool. O {@link TaskDecorator} de {@link AsyncConfig} deve carregar o
 * inquilino da submissão para dentro da thread do pool — senão as tasks (chat IA, prontuário IA, SMS
 * convite) rodariam no schema {@code public} (inquilino errado). Testa os 3 executores.
 */
@SpringBootTest
class AsyncTenantPropagationTest {

    private static final String SCHEMA = "inq_teste_async";

    @Autowired
    @Qualifier("chatIaExecutor")
    private Executor chatIaExecutor;
    @Autowired
    @Qualifier("prontuarioIaExecutor")
    private Executor prontuarioIaExecutor;
    @Autowired
    @Qualifier("smsConviteExecutor")
    private Executor smsConviteExecutor;

    @Test
    void propagaInquilinoParaAThreadAsync() throws Exception {
        Map<String, Executor> executores = Map.of(
                "chatIaExecutor", chatIaExecutor,
                "prontuarioIaExecutor", prontuarioIaExecutor,
                "smsConviteExecutor", smsConviteExecutor);

        for (Map.Entry<String, Executor> e : executores.entrySet()) {
            AtomicReference<String> vistoNaThreadDoPool = new AtomicReference<>();
            CountDownLatch pronto = new CountDownLatch(1);

            String anterior = TenantContext.atualBruto();
            try {
                TenantContext.definir(SCHEMA); // simula a thread do request com inquilino resolvido
                e.getValue().execute(() -> {
                    vistoNaThreadDoPool.set(TenantContext.atual());
                    pronto.countDown();
                });
            } finally {
                if (anterior != null) {
                    TenantContext.definir(anterior);
                } else {
                    TenantContext.limpar();
                }
            }

            assertTrue(pronto.await(5, TimeUnit.SECONDS), "a task async rodou (" + e.getKey() + ")");
            assertEquals(SCHEMA, vistoNaThreadDoPool.get(),
                    "o inquilino NÃO foi propagado para a thread do pool (" + e.getKey() + ")");
        }
    }
}
