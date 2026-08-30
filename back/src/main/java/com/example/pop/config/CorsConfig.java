package com.example.pop.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Libera o acesso do front (Angular) / app. As origens permitidas vêm da
 * variável de ambiente APP_CORS_ALLOWED_ORIGINS (lista separada por vírgula).
 * O Spring Security usa este bean (.cors) para tratar o CORS da API, inclusive
 * o header Authorization usado no JWT.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:4300,http://localhost:8081,http://localhost:19006}")
    private String[] allowedOrigins;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);

        // O handshake do WebSocket (/ws) chega do app NATIVO com um Origin
        // imprevisível (o navegador manda o do front, que está na lista; o app
        // manda null/file/etc. e levava 403 "Invalid CORS request"). A segurança
        // do WS é o token no frame CONNECT (ChatChannelInterceptor), não o CORS —
        // então o handshake aceita qualquer origem.
        CorsConfiguration wsConfig = new CorsConfiguration();
        wsConfig.setAllowedOriginPatterns(List.of("*"));
        wsConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        wsConfig.setAllowedHeaders(List.of("*"));
        wsConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Padrões específicos ANTES do genérico (o /ws pega a config permissiva).
        source.registerCorsConfiguration("/ws", wsConfig);
        source.registerCorsConfiguration("/ws/**", wsConfig);
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
