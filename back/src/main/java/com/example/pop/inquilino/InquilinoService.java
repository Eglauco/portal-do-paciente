package com.example.pop.inquilino;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
    private final ConcurrentHashMap<String, String> schemaPorSubdominio = new ConcurrentHashMap<>();
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

    /**
     * Schema do inquilino cujo subdomínio é o dado (ex.: {@code "nascente"}) — resolve a clínica ANTES
     * do login, a partir do header {@code X-Inquilino} que o front deriva do hostname. Retorna
     * {@code null} se o subdomínio é desconhecido (o chamador então deixa o contexto sem inquilino =
     * branding da plataforma). Cacheia só os ACERTOS: o registro de inquilinos é pequeno, e não cachear
     * os erros impede que subdomínios forjados inflem a memória.
     */
    public String schemaPorSubdominio(String subdominio) {
        if (subdominio == null || subdominio.isBlank()) {
            return null;
        }
        String cacheado = schemaPorSubdominio.get(subdominio);
        if (cacheado != null) {
            return cacheado;
        }
        String schema = repository.findBySubdominio(subdominio).map(Inquilino::getSchemaName).orElse(null);
        if (schema != null) {
            schemaPorSubdominio.put(subdominio, schema);
        }
        return schema;
    }

    /**
     * Id do inquilino do schema dado (reverso de {@link #schemaPorId}) — usado no login para pôr o
     * claim {@code inq} quando o inquilino foi resolvido pelo SUBDOMÍNIO. {@code null} se desconhecido.
     * Sem cache: só é chamado no login (raro), e assim nunca devolve um id obsoleto se um schema for
     * reaproveitado.
     */
    public Long idPorSchema(String schema) {
        if (schema == null) {
            return null;
        }
        return repository.findBySchemaName(schema).map(Inquilino::getId).orElse(null);
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

    /** Resumo de uma clínica para o seletor pós-login do app compartilhado. */
    public record Clinica(Long id, String nome) {
    }

    /**
     * Clínicas ATIVAS entre os ids dados (ordenadas por nome), para o seletor "Selecionar Clínica" do app
     * compartilhado quando o CPF está em mais de uma. Sem cache (login é raro; evita listar clínica inativa).
     */
    public List<Clinica> clinicasAtivas(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids).stream()
                .filter(i -> "ATIVO".equalsIgnoreCase(i.getSituacao()))
                .map(i -> new Clinica(i.getId(), i.getNome()))
                .sorted(Comparator.comparing(Clinica::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
