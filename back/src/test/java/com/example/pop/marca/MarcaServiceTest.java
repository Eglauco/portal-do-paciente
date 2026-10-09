package com.example.pop.marca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.pop.tenant.TenantContext;

/**
 * Read-path da marca. Os testes rodam contra o banco de DEV real (que o admin altera:
 * white-label de textos, upload de logo/fundo), então NÃO se asserta valores exatos —
 * só o contrato: os textos nunca ficam vazios (fallback ao padrão) e o nome é consistente.
 * O seed (V81/V82/V83) já é validado pelo Flyway ao subir o contexto.
 */
@SpringBootTest
class MarcaServiceTest {

    @Autowired
    private MarcaService marcaService;

    @Test
    void marcaSempreDevolveTextosNaoVazios() {
        TenantContext.limpar(); // pré-login (public = plataforma): marca NEUTRA, sem config de inquilino
        MarcaResponse m = marcaService.marca();
        assertNotNull(m.nomePlataforma());
        assertFalse(m.nomePlataforma().isBlank(), "nome nunca vazio (cai no padrão)");
        assertFalse(m.loginTitulo().isBlank(), "título nunca vazio (cai no padrão)");
        assertFalse(m.loginSubtitulo().isBlank(), "subtítulo nunca vazio (cai no padrão)");
        // #6: pré-login serve a marca NEUTRA de plataforma (defaults), sem ler config de inquilino.
        assertEquals(MarcaService.NOME_PADRAO, m.nomePlataforma(), "pré-login: nome padrão da plataforma");
        assertEquals(MarcaService.TITULO_PADRAO, m.loginTitulo());
        assertNull(m.logoUrl(), "pré-login: sem logo de inquilino");
        assertNull(m.loginFundoUrl(), "pré-login: sem imagem de fundo de inquilino");
    }

    @Test
    void nomePlataformaReaproveitadoPeloRodapeDoPdf() {
        // Mesmo valor lido por marca() e pelo helper usado no rodapé do relatório.
        assertEquals(marcaService.marca().nomePlataforma(), marcaService.nomePlataforma());
    }
}
