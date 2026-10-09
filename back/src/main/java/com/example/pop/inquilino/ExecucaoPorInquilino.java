package com.example.pop.inquilino;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.pop.tenant.TenantContext;

/**
 * Executa uma ação uma vez POR INQUILINO ATIVO, com o {@link TenantContext} fixado no schema de cada um.
 *
 * <p>Usado pelos jobs {@code @Scheduled} (e qualquer trabalho fora de request): eles rodam sem inquilino
 * resolvido, então, sem isto, operariam só no schema {@code public}. Pula o inquilino PADRÃO (schema
 * {@code public}) — ele é PLATAFORMA, não um inquilino com dados de domínio. Isola falhas: o erro de um
 * inquilino é logado e NÃO impede os demais; o contexto da thread é restaurado no fim.
 */
@Component
public class ExecucaoPorInquilino {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoPorInquilino.class);

    private final InquilinoRepository repository;

    public ExecucaoPorInquilino(InquilinoRepository repository) {
        this.repository = repository;
    }

    /** Roda {@code acao} (recebe o schema) para cada inquilino ATIVO ≠ public, com o tenant fixado. */
    public void paraCadaInquilinoAtivo(Consumer<String> acao) {
        String anterior = TenantContext.atualBruto();
        try {
            for (Inquilino inquilino : repository.findAll()) {
                if (!"ATIVO".equals(inquilino.getSituacao())) {
                    continue;
                }
                String schema = inquilino.getSchemaName();
                // O inquilino padrão (public) é plataforma — jobs de domínio não rodam nele.
                if (TenantContext.SCHEMA_PADRAO.equals(schema)) {
                    continue;
                }
                TenantContext.definir(schema);
                try {
                    acao.accept(schema);
                } catch (RuntimeException e) {
                    // Isola o inquilino: um erro não pode impedir o job de rodar nos demais.
                    log.warn("Job por-inquilino falhou no schema '{}': {}", schema, e.getMessage(), e);
                }
            }
        } finally {
            if (anterior != null) {
                TenantContext.definir(anterior);
            } else {
                TenantContext.limpar();
            }
        }
    }
}
