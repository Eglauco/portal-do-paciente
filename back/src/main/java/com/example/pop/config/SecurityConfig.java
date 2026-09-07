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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import java.time.Instant;

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
                        .requestMatchers("/auth/login", "/paciente-auth/ativar", "/paciente-auth/solicitar-codigo").permitAll()
                        .requestMatchers(HttpMethod.GET, "/motivo-falta/ativos", "/categoria-nps/ativos").permitAll()
                        .requestMatchers("/dispositivo").permitAll()
                        .requestMatchers(HttpMethod.GET, "/postagem/*/comentarios").permitAll()
                        // Feed agora é do paciente logado (filtrado pelas unidades vinculadas a ele).
                        .requestMatchers("/feed", "/feed/**").hasRole("PACIENTE")
                        .requestMatchers(HttpMethod.POST, "/postagem/*/curtir").permitAll()
                        // App do paciente logado.
                        .requestMatchers(HttpMethod.POST, "/postagem/*/comentarios", "/postagem/*/comentarios/*/responder")
                        .hasRole("PACIENTE")
                        // Editar/excluir o próprio comentário (o dono é conferido no controller).
                        .requestMatchers(HttpMethod.PUT, "/postagem/*/comentarios/*").hasRole("PACIENTE")
                        .requestMatchers(HttpMethod.DELETE, "/postagem/*/comentarios/*").hasRole("PACIENTE")
                        .requestMatchers("/meu/**").hasRole("PACIENTE")
                        .requestMatchers("/paciente-auth/**").authenticated() // /me
                        // Back-office (admin): o front do admin envia o token em todas as chamadas.
                        .requestMatchers("/auth/**", "/paciente/**", "/prontuario/**", "/storage/**", "/usuario/**",
                                "/agendamento/**", "/nps/**", "/chat/**", "/unidade/**", "/especialidade/**",
                                "/procedimento/**", "/profissional/**", "/motivo-falta/**", "/categoria-nps/**",
                                "/postagem/**", "/sau/**", "/tipo-manifestacao/**", "/dashboard/**",
                                "/perfil/**")
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
    JwtDecoder jwtDecoder(UsuarioRepository usuarioRepository) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chaveJwt).build();
        // Além da validação padrão (expiração), rejeita tokens ADMIN emitidos ANTES da
        // última troca de senha do usuário — trocar a senha derruba todas as sessões.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                tokenNaoRevogado(usuarioRepository)));
        return decoder;
    }

    /** Invalida tokens ADMIN cujo "iat" é anterior ao credenciaisAlteradasEm do usuário. */
    private static OAuth2TokenValidator<Jwt> tokenNaoRevogado(UsuarioRepository usuarioRepository) {
        return jwt -> {
            if (!"ADMIN".equals(jwt.getClaimAsString("role"))) {
                return OAuth2TokenValidatorResult.success(); // só o admin tem versionamento de senha
            }
            Object uid = jwt.getClaim("uid");
            Instant emitidoEm = jwt.getIssuedAt();
            if (!(uid instanceof Number numero) || emitidoEm == null) {
                return OAuth2TokenValidatorResult.success();
            }
            Instant alteradaEm = usuarioRepository.findById(numero.longValue())
                    .map(Usuario::getCredenciaisAlteradasEm)
                    .orElse(null);
            if (alteradaEm != null && emitidoEm.isBefore(alteradaEm)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Sessão encerrada por troca de senha", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
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
