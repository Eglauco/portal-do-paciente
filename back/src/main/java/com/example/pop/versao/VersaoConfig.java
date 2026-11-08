package com.example.pop.versao;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Carrega o registro de versoes (resources/versao.properties) no Environment, para o
 * {@link VersaoService} resolver via @Value. Fica numa @Configuration (e nao no @Service)
 * porque @PropertySource so e garantido em classes de configuracao.
 *
 * <p>ignoreResourceNotFound=true + defaults no service: se o arquivo faltar, o app sobe
 * assim mesmo (exibindo versoes-sentinela), em vez de quebrar o boot.
 */
@Configuration
@PropertySource(value = "classpath:versao.properties", ignoreResourceNotFound = true)
public class VersaoConfig {
}
