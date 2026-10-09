package com.example.pop.assinatura;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Roteamento de WEBHOOK de assinatura (multi-inquilino): mapeia a CHAVE do documento no provedor →
 * SCHEMA do inquilino dono do termo. Vive SEMPRE no schema {@code public} (tabela de PLATAFORMA) — o
 * webhook chega SEM JWT, então não há {@code TenantContext} resolvido; esta é a ponte para o inquilino.
 * A escrita acontece ao criar a cerimônia (request de paciente logado, com o tenant já setado).
 */
@Entity
@Table(name = "assinatura_roteamento", schema = "public")
public class AssinaturaRoteamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chave do documento como o WEBHOOK a apresenta (ZapSign/Autentique: token; Clicksign: env/doc; DocuSign: envelope). */
    @Column(nullable = false, length = 160)
    private String chave;

    @Column(name = "schema_name", nullable = false, length = 63)
    private String schemaName;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }
}
