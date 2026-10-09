package com.example.pop.assinatura;

import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Mantém o roteamento {@code chave do documento → schema do inquilino} ({@link AssinaturaRoteamento}, no
 * {@code public}). Escrito ao criar a cerimônia de assinatura (paciente logado, tenant setado); lido pelo
 * webhook (que chega SEM JWT) para achar o inquilino dono do termo antes de tocar config/domínio.
 */
@Service
public class AssinaturaRoteamentoService {

    private final AssinaturaRoteamentoRepository repository;

    public AssinaturaRoteamentoService(AssinaturaRoteamentoRepository repository) {
        this.repository = repository;
    }

    /** Garante o ponteiro {@code chave → schema} (idempotente; atualiza se a chave migrou de schema). */
    public void registrar(String chave, String schema) {
        if (chave == null || chave.isBlank() || schema == null || schema.isBlank()) {
            return;
        }
        repository.findByChave(chave).ifPresentOrElse(r -> {
            if (!schema.equals(r.getSchemaName())) {
                r.setSchemaName(schema);
                repository.save(r);
            }
        }, () -> {
            AssinaturaRoteamento r = new AssinaturaRoteamento();
            r.setChave(chave);
            r.setSchemaName(schema);
            repository.save(r);
        });
    }

    /** Schema do inquilino dono da chave; vazio se a chave for desconhecida. */
    public Optional<String> schemaDaChave(String chave) {
        return chave == null || chave.isBlank() ? Optional.empty()
                : repository.findByChave(chave).map(AssinaturaRoteamento::getSchemaName);
    }
}
