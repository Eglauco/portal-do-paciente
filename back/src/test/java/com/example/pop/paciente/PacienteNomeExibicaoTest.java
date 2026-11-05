package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Regra do nome social (Decreto nº 8.727/2016; Portaria MS nº 1.820/2009): o nome de EXIBIÇÃO
 * usa o nome social quando o paciente opta por usá-lo; o nome civil é preservado e aparece junto
 * nos documentos legais. Teste puro da entidade (sem banco/contexto).
 */
class PacienteNomeExibicaoTest {

    private static Paciente paciente(String nome, String nomeSocial, boolean utiliza) {
        Paciente p = new Paciente();
        p.setNome(nome);
        p.setNomeSocial(nomeSocial);
        p.setUtilizaNomeSocial(utiliza);
        return p;
    }

    @Test
    void semNomeSocialUsaOCivil() {
        Paciente p = paciente("Maria de Souza", null, false);
        assertEquals("Maria de Souza", p.nomeExibicao());
        assertEquals("Maria de Souza", p.nomeDocumentoLegal());
    }

    @Test
    void comNomeSocialMasSemUtilizarAindaUsaOCivil() {
        Paciente p = paciente("Maria de Souza", "Rebeca", false);
        assertEquals("Maria de Souza", p.nomeExibicao());
        assertEquals("Maria de Souza", p.nomeDocumentoLegal());
    }

    @Test
    void utilizandoNomeSocialExibeOSocialEDocumentoMostraOsDois() {
        Paciente p = paciente("Maria de Souza", "Rebeca", true);
        assertEquals("Rebeca", p.nomeExibicao(), "atendimento exibe o nome social");
        assertEquals("Rebeca (Maria de Souza)", p.nomeDocumentoLegal(), "documento legal mostra os dois");
    }

    @Test
    void utilizarComNomeSocialEmBrancoCaiNoCivil() {
        Paciente p = paciente("Maria de Souza", "   ", true);
        assertEquals("Maria de Souza", p.nomeExibicao());
        assertEquals("Maria de Souza", p.nomeDocumentoLegal());
    }
}
