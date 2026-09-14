package com.example.pop.marca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
        MarcaResponse m = marcaService.marca();
        assertNotNull(m.nomePlataforma());
        assertFalse(m.nomePlataforma().isBlank(), "nome nunca vazio (cai no padrão)");
        assertFalse(m.loginTitulo().isBlank(), "título nunca vazio (cai no padrão)");
        assertFalse(m.loginSubtitulo().isBlank(), "subtítulo nunca vazio (cai no padrão)");
        // logoUrl / loginFundoUrl podem existir (admin enviou imagens) ou ser null — sem asserção de valor.
    }

    @Test
    void nomePlataformaReaproveitadoPeloRodapeDoPdf() {
        // Mesmo valor lido por marca() e pelo helper usado no rodapé do relatório.
        assertEquals(marcaService.marca().nomePlataforma(), marcaService.nomePlataforma());
    }
}
