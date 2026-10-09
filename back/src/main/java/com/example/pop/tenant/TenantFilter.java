package com.example.pop.tenant;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.pop.inquilino.InquilinoService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Define o {@link TenantContext} da requisição a partir do claim {@code inq} do JWT autenticado
 * (NUNCA de parâmetro/header do request — o inquilino vem sempre do principal). Roda DEPOIS da
 * autenticação (Bearer), então a {@code Authentication} já está no contexto. Limpa SEMPRE no
 * finally, para a thread do pool não vazar o schema para a próxima requisição.
 *
 * <p>Requisições sem JWT (endpoints públicos) ficam no schema padrão ({@code public}). As bordas
 * fora do request (@Async, scheduler, WebSocket, webhooks) NÃO passam por aqui — serão tratadas
 * na Fase 2.
 *
 * <p>NÃO é {@code @Component} de propósito: um bean {@code Filter} seria auto-registrado pelo Spring
 * Boot no chain do servlet (antes da autenticação) e o {@code OncePerRequestFilter} puraria a
 * execução correta dentro da cadeia de segurança. É instanciado no {@code SecurityConfig}.
 */
public class TenantFilter extends OncePerRequestFilter {

    private final InquilinoService inquilinoService;

    public TenantFilter(InquilinoService inquilinoService) {
        this.inquilinoService = inquilinoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getClaim("inq") instanceof Number inq) {
                String schema = inquilinoService.schemaPorId(inq.longValue());
                TenantContext.definir(schema);
                MDC.put("inquilino", schema); // rastreabilidade: o inquilino aparece nos logs da requisição
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove("inquilino");
            TenantContext.limpar();
        }
    }
}
