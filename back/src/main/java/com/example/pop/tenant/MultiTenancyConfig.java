package com.example.pop.tenant;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MultiTenancyConfig.class);

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
    FlywayMigrationStrategy flywayMigrationStrategy(ProvisionamentoService provisionamento, DataSource dataSource) {
        return flyway -> {
            flyway.migrate(); // db/platform -> public (inclui a tabela public.inquilino)
            provisionamento.garantirInquilinoPadrao(TenantContext.SCHEMA_PADRAO);
            // Aplica as migrations de domínio (db/migration) em TODOS os inquilinos JÁ provisionados.
            // Sem isto, uma migration nova só chega no schema padrão e os inquilinos EXISTENTES quebram
            // ao usar a coluna/tabela nova (o flyway.migrate() do boot só migra o public/padrão).
            // Idempotente: o Flyway só roda as pendentes de cada schema. Falha de um tenant NÃO derruba
            // o boot (loga e segue) — evita tirar toda a plataforma do ar por um único schema problemático.
            for (String schema : schemasDeInquilino(dataSource)) {
                try {
                    provisionamento.provisionarSchema(schema);
                } catch (RuntimeException e) {
                    log.error("Falha ao migrar o schema do inquilino '{}': {}", schema, e.getMessage());
                }
            }
        };
    }

    /** Schemas de inquilino já provisionados (public.inquilino), exceto o padrão (migrado acima). */
    private static List<String> schemasDeInquilino(DataSource dataSource) {
        List<String> schemas = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT schema_name FROM public.inquilino")) {
            while (rs.next()) {
                String schema = rs.getString(1);
                if (schema != null && !schema.isBlank() && !TenantContext.SCHEMA_PADRAO.equals(schema)) {
                    schemas.add(schema);
                }
            }
        } catch (SQLException e) {
            log.error("Falha ao listar schemas de inquilino para migrar: {}", e.getMessage());
        }
        return schemas;
    }
}
