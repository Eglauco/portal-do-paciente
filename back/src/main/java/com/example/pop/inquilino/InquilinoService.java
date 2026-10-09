package com.example.pop.inquilino;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.pop.tenant.TenantContext;

/**
 * Resolve o schema de um inquilino (a partir do id que vai no claim {@code inq} do JWT) e conhece o
 * inquilino PADRÃO (o do schema {@code public}, que guarda os dados atuais). Cacheia em memória —
 * o registro de inquilinos é pequeno e muda raramente (só ao provisionar/remover, Fase 1+).
 */
@Service
public class InquilinoService {

    private final InquilinoRepository repository;
    private final ConcurrentHashMap<Long, String> schemaPorId = new ConcurrentHashMap<>();
    private volatile Long idPadrao;

    public InquilinoService(InquilinoRepository repository) {
        this.repository = repository;
    }

    /** Schema do inquilino do id dado (cacheado). Id nulo/desconhecido → schema padrão (public). */
    public String schemaPorId(Long id) {
        if (id == null) {
            return TenantContext.SCHEMA_PADRAO;
        }
        return schemaPorId.computeIfAbsent(id, i ->
                repository.findById(i).map(Inquilino::getSchemaName).orElse(TenantContext.SCHEMA_PADRAO));
    }

    /** Id do inquilino padrão (schema {@code public}). Invariante do sistema — semeado na V148. */
    public Long idPadrao() {
        Long id = idPadrao;
        if (id == null) {
            id = repository.findBySchemaName(TenantContext.SCHEMA_PADRAO).map(Inquilino::getId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Inquilino padrão (schema public) não encontrado — faltou a migration V148?"));
            idPadrao = id;
        }
        return id;
    }
}
