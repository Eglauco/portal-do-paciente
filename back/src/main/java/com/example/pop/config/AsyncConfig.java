package com.example.pop.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.pop.tenant.TenantContext;

/**
 * Habilita processamento assíncrono (@Async) e provê os executores (IA do chat, análise de prontuário,
 * convite por SMS). A IA/SMS rodam fora da thread do request (chamadas lentas): o paciente recebe a
 * resposta do próprio envio na hora e o trabalho pesado segue em background.
 *
 * <p>Multi-inquilino: o {@link TenantContext} é um ThreadLocal e NÃO é herdado pelas threads do pool.
 * Sem propagação, toda task @Async rodaria no schema {@code public} (inquilino errado/vazio). Cada
 * executor leva um {@link TaskDecorator} que captura o inquilino na submissão (thread do request) e o
 * aplica dentro da thread do pool, limpando no fim (não vaza para a próxima task do pool).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Propaga o inquilino do {@link TenantContext} para a thread do pool: captura o schema na SUBMISSÃO
     * (decorate roda na thread chamadora, que tem o tenant) e o re-seta ao executar, restaurando o
     * estado anterior da thread do pool no finally.
     */
    private static TaskDecorator tenantDecorator() {
        return runnable -> {
            String schema = TenantContext.atualBruto(); // thread do request (com inquilino)
            return () -> {
                String anterior = TenantContext.atualBruto();
                if (schema != null) {
                    TenantContext.definir(schema);
                } else {
                    TenantContext.limpar();
                }
                try {
                    runnable.run();
                } finally {
                    if (anterior != null) {
                        TenantContext.definir(anterior);
                    } else {
                        TenantContext.limpar();
                    }
                }
            };
        };
    }

    /** Pool pequeno e limitado para as chamadas de IA do chat (não deixa a IA esgotar threads). */
    @Bean(name = "chatIaExecutor")
    public Executor chatIaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-ia-");
        executor.setTaskDecorator(tenantDecorator());
        executor.initialize();
        return executor;
    }

    /** Pool da análise de documentos do prontuário (Opus + PDF é lento; pool pequeno e enfileirado). */
    @Bean(name = "prontuarioIaExecutor")
    public Executor prontuarioIaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("prontuario-ia-");
        executor.setTaskDecorator(tenantDecorator());
        executor.initialize();
        return executor;
    }

    /** Pool do convite por SMS ao agendar (POST ao Twilio; pequeno, enfileirado e fail-open). */
    @Bean(name = "smsConviteExecutor")
    public Executor smsConviteExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("sms-convite-");
        executor.setTaskDecorator(tenantDecorator());
        executor.initialize();
        return executor;
    }
}
