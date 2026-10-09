package com.example.pop.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
