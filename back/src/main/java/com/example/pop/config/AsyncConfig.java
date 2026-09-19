package com.example.pop.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Habilita processamento assíncrono (@Async) e provê o executor do atendimento por IA do chat.
 * A IA roda fora da thread do request (a chamada ao Claude é lenta): o paciente recebe a resposta
 * do próprio envio na hora, e a resposta da IA chega depois via WebSocket/push.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Pool pequeno e limitado para as chamadas de IA do chat (não deixa a IA esgotar threads). */
    @Bean(name = "chatIaExecutor")
    public Executor chatIaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-ia-");
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
        executor.initialize();
        return executor;
    }
}
