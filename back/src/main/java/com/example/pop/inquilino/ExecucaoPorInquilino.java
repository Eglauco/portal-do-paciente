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
 * resolvido, então, sem isto, operariam só no schema padrão. Processa TODOS os inquilinos ATIVO com
 * dados de domínio — inclusive o {@code principal} (o inquilino padrão/fallback, que TEM domínio); só
 * pula um eventual registro cujo schema seja {@code public} (a PLATAFORMA pura, sem tabelas de domínio).
 * Isola falhas: o erro de um inquilino é logado e NÃO impede os demais; o contexto é restaurado no fim.
 */
@Component
public class ExecucaoPorInquilino {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoPorInquilino.class);

    private final InquilinoRepository repository;

    public ExecucaoPorInquilino(InquilinoRepository repository) {
        this.repository = repository;
    }

    /** Roda {@code acao} (recebe o schema) para cada inquilino ATIVO com domínio (≠ public), com o tenant fixado. */
    public void paraCadaInquilinoAtivo(Consumer<String> acao) {
        String anterior = TenantContext.atualBruto();
        try {
            for (Inquilino inquilino : repository.findAll()) {
                if (!"ATIVO".equals(inquilino.getSituacao())) {
                    continue;
                }
                String schema = inquilino.getSchemaName();
                // Pula só o 'public' (plataforma pura, sem tabelas de domínio). O 'principal' (schema padrão)
                // É um inquilino de domínio real e DEVE receber os jobs — não confundir com a plataforma.
                if ("public".equals(schema)) {
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
