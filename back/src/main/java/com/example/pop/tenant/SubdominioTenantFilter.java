package com.example.pop.tenant;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.pop.inquilino.InquilinoService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolve o inquilino PRÉ-LOGIN a partir do subdomínio do front (header {@code X-Inquilino}, que o
 * front deriva de {@code window.location.hostname} — ex.: {@code "nascente"} em
 * nascente.trilharsaude.app.br). Front e back ficam em hosts diferentes, então o back NÃO vê o
 * subdomínio no próprio Host: o front o envia no header. Serve só para o BRANDING público
 * (tema/marca/funcionalidades mostrarem a cara da clínica na tela de login do back-office).
 *
 * <p>Só age quando NÃO há inquilino resolvido ({@code atualBruto()==null}) — ou seja, SEM JWT. Com JWT,
 * o {@link TenantFilter} (que roda ANTES deste) já fixou o schema pelo claim {@code inq}, e o header é
 * ignorado: o JWT SEMPRE manda. O header é spoofável, mas só afeta branding público pré-login (nenhum
 * dado sensível depende dele), então o impacto de um valor forjado é nulo. Limpa no finally SÓ o que
 * ESTE filtro setou.
 *
 * <p>NÃO é {@code @Component} (mesmo motivo do {@link TenantFilter}): é instanciado no SecurityConfig e
 * encaixado DEPOIS do TenantFilter.
 */
public class SubdominioTenantFilter extends OncePerRequestFilter {

    /** Rótulo DNS válido (minúsculas, dígitos, hífen; até 63). Barra valores estranhos/forjados. */
    private static final Pattern ROTULO_VALIDO = Pattern.compile("^[a-z0-9-]{1,63}$");

    private final InquilinoService inquilinoService;

    public SubdominioTenantFilter(InquilinoService inquilinoService) {
        this.inquilinoService = inquilinoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean definiuAqui = false;
        try {
            if (TenantContext.atualBruto() == null) {
                String rotulo = request.getHeader("X-Inquilino");
                if (rotulo != null) {
                    rotulo = rotulo.trim().toLowerCase();
                    if (ROTULO_VALIDO.matcher(rotulo).matches()) {
                        String schema = inquilinoService.schemaPorSubdominio(rotulo);
                        if (schema != null) {
                            TenantContext.definir(schema);
                            MDC.put("inquilino", schema);
                            definiuAqui = true;
                        }
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            if (definiuAqui) {
                MDC.remove("inquilino");
                TenantContext.limpar();
            }
        }
    }
}
