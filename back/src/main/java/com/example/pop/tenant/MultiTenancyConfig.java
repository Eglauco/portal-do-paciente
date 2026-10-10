package com.example.pop.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.pop.inquilino.ProvisionamentoService;

/**
 * Liga o multi-tenancy por SCHEMA no Hibernate, registrando o connection provider e o resolver de
 * inquilino. Em Hibernate 6/7, basta fornecer esses dois que a multi-tenancy fica ativa (não há
 * mais a propriedade {@code hibernate.multiTenancy}).
 */
@Configuration
public class MultiTenancyConfig {

    @Bean
    HibernatePropertiesCustomizer multiTenancyHibernateCustomizer(
            MultiTenantConnectionProvider<String> connectionProvider,
            CurrentTenantIdentifierResolver<String> tenantResolver) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }

    /**
     * Bootstrap das migrations (Design B), ANTES do Hibernate validar: aplica as migrations no {@code public}
     * (plataforma + o UPDATE que repont. o inquilino padrão) e garante o schema do inquilino PADRÃO
     * ({@code principal}, nomeado) com as tabelas de DOMÍNIO. Assim o {@code getAnyConnection} do Hibernate
     * (que usa o schema padrão) valida contra um schema que TEM as tabelas de domínio, e os testes (que rodam
     * sem inquilino resolvido) também operam nele.
     */
    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy(ProvisionamentoService provisionamento) {
        return flyway -> {
            flyway.migrate();
            provisionamento.garantirInquilinoPadrao(TenantContext.SCHEMA_PADRAO);
        };
    }
}
