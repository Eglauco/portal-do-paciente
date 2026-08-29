package com.example.pop.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Segurança da API: autenticação stateless via JWT (Bearer no header).
 * Por enquanto só /auth/me exige token; o restante segue aberto (o app mobile
 * ainda não faz login). O login do admin é exigido/aplicado no front.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecretKey chaveJwt;

    public SecurityConfig(@Value("${app.jwt.secret}") String segredo) {
        this.chaveJwt = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Públicos (sem token).
                        .requestMatchers("/auth/login", "/paciente-auth/ativar").permitAll()
                        .requestMatchers(HttpMethod.GET, "/motivo-falta/ativos", "/categoria-nps/ativos").permitAll()
                        .requestMatchers("/feed/**", "/dispositivo").permitAll()
                        .requestMatchers(HttpMethod.GET, "/postagem/*/comentarios").permitAll()
                        .requestMatchers(HttpMethod.POST, "/postagem/*/curtir").permitAll()
                        // App do paciente logado.
                        .requestMatchers(HttpMethod.POST, "/postagem/*/comentarios", "/postagem/*/comentarios/*/responder")
                        .hasRole("PACIENTE")
                        .requestMatchers("/meu/**").hasRole("PACIENTE")
                        .requestMatchers("/paciente-auth/**").authenticated() // /me
                        // Back-office (admin): o front do admin envia o token em todas as chamadas.
                        .requestMatchers("/auth/**", "/paciente/**", "/prontuario/**", "/storage/**", "/usuario/**",
                                "/agendamento/**", "/nps/**", "/chat/**", "/unidade/**", "/especialidade/**",
                                "/procedimento/**", "/profissional/**", "/motivo-falta/**", "/categoria-nps/**",
                                "/postagem/**")
                        .hasRole("ADMIN")
                        // /ws (handshake do WebSocket) e o que não foi listado seguem abertos por ora (a Fase 4B tranca o WS).
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /** Mapeia o claim "role" do JWT (ADMIN / PACIENTE) para uma authority ROLE_*. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
            String papel = jwt.getClaimAsString("role");
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            if (papel != null && !papel.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + papel));
            }
            return authorities;
        });
        return conversor;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(chaveJwt).build();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chaveJwt));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
