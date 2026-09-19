package com.example.pop.prontuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.pop.prontuario.AnaliseDocumentoService.AnaliseResultado;

/** Parsing da decisão da análise: alerta (AGUARDANDO_VALIDACAO) x sem alterações, e limpeza do token. */
class AnaliseDocumentoServiceTest {

    // storage null e sem chave: só exercita interpretar() (não faz rede nem baixa bytes).
    private final AnaliseDocumentoService service =
            new AnaliseDocumentoService(null, "", "claude-opus-5", 1500, 90);

    @Test
    void semValidacaoNuncaGeraAlerta() {
        AnaliseResultado r = service.interpretar("Hemograma dentro da normalidade.", false);
        assertEquals(StatusAnaliseDocumento.SEM_ALTERACOES, r.status());
        assertTrue(r.resumo().contains("Hemograma"));
    }

    @Test
    void marcadorGeraAguardandoValidacaoEremoveToken() {
        AnaliseResultado r = service.interpretar(
                "ALERTA_PRONTUARIO\nLaudo indica achado suspeito de malignidade (trecho: \"...\").", true);
        assertEquals(StatusAnaliseDocumento.AGUARDANDO_VALIDACAO, r.status());
        assertFalse(r.resumo().contains("ALERTA_PRONTUARIO"));
        assertTrue(r.resumo().toLowerCase().contains("laudo"));
    }

    @Test
    void marcadorComPreambuloTambemAlerta() {
        // O modelo pode escrever antes do marcador; deve detectar em qualquer posição.
        AnaliseResultado r = service.interpretar(
                "Resumo: valores alterados.\nALERTA_PRONTUARIO\nHemoglobina muito baixa.", true);
        assertEquals(StatusAnaliseDocumento.AGUARDANDO_VALIDACAO, r.status());
        assertFalse(r.resumo().contains("ALERTA_PRONTUARIO"));
    }

    @Test
    void semMarcadorFicaSemAlteracoes() {
        AnaliseResultado r = service.interpretar("Todos os valores dentro da referência.", true);
        assertEquals(StatusAnaliseDocumento.SEM_ALTERACOES, r.status());
    }

    @Test
    void respostaVaziaNaoViraSemAlteracoes() {
        // Fail-safe clínico: resposta vazia NUNCA vira "sem alterações"; fica não analisado.
        AnaliseResultado r = service.interpretar("   ", true);
        assertEquals(StatusAnaliseDocumento.NAO_ANALISADO, r.status());
        assertNull(r.resumo());
    }
}
