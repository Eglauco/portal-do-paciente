package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Testa a validação de dígito verificador (sem subir o contexto Spring). */
class DocumentosTest {

    @Test
    void somenteDigitosLimpaMascaraENulls() {
        assertEquals("52998224725", Documentos.somenteDigitos("529.982.247-25"));
        assertEquals("11900000001", Documentos.somenteDigitos("(11) 90000-0001"));
        assertNull(Documentos.somenteDigitos("   "));
        assertNull(Documentos.somenteDigitos(null));
    }

    @Test
    void cpfValidoAceitaCorretoRejeitaErrado() {
        assertTrue(Documentos.cpfValido("529.982.247-25"));
        assertTrue(Documentos.cpfValido("111.444.777-35"));
        assertFalse(Documentos.cpfValido("529.982.247-24"), "dígito verificador errado");
        assertFalse(Documentos.cpfValido("111.111.111-11"), "sequência repetida");
        assertFalse(Documentos.cpfValido("123"), "tamanho errado");
        assertFalse(Documentos.cpfValido(null));
    }

    @Test
    void cnsValidoAceitaCorretoRejeitaErrado() {
        assertTrue(Documentos.cnsValido("798345678901233"), "provisório (7) com soma % 11 == 0");
        assertFalse(Documentos.cnsValido("700000000000001"), "soma % 11 != 0");
        assertFalse(Documentos.cnsValido("123456789012345"), "início inválido");
        assertFalse(Documentos.cnsValido("798"), "tamanho errado");
        assertFalse(Documentos.cnsValido(null));
    }
}
