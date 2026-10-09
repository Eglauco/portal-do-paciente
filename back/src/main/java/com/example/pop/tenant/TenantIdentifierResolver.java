package com.example.pop.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Diz ao Hibernate qual é o inquilino (schema) da unidade de trabalho atual, lendo do
 * {@link TenantContext}. Fase 0.1: sem ninguém definir o contexto, resolve sempre para
 * {@code public} (comportamento atual).
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.atual();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Garante que uma sessão aberta não seja reutilizada para outro inquilino.
        return true;
    }
}
