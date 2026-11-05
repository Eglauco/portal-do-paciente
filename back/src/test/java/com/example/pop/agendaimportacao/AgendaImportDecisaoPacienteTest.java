package com.example.pop.agendaimportacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.pop.agendaimportacao.AgendaImportacaoService.AcaoPaciente;
import com.example.pop.agendaimportacao.AgendaImportacaoService.TipoIdentificador;

/**
 * Matriz de decisão da regra de cadastro automático de paciente na importação de agenda. Função pura (sem DB):
 * - achou pelo identificador → EXISTENTE (aplica a conferência de CPF);
 * - id interno não achado → ERRO (nunca cria por id interno);
 * - prontuário/código não achado: achou pelo CPF → VINCULAR_POR_CPF; senão com dados completos → CRIAR_NOVO;
 *   senão → ERRO pedindo os dados obrigatórios.
 */
class AgendaImportDecisaoPacienteTest {

    @Test
    void achadoPeloIdentificador_sempreExistente() {
        for (TipoIdentificador tipo : TipoIdentificador.values()) {
            assertThat(AgendaImportacaoService.decidirAcao(tipo, true, false, false))
                    .as("achou por %s", tipo).isEqualTo(AcaoPaciente.EXISTENTE_POR_IDENTIFICADOR);
            // Mesmo que também exista por CPF e os dados estejam completos, achar pelo identificador vence.
            assertThat(AgendaImportacaoService.decidirAcao(tipo, true, true, true))
                    .isEqualTo(AcaoPaciente.EXISTENTE_POR_IDENTIFICADOR);
        }
    }

    @Test
    void idInternoNaoAchado_eErro_nuncaCria() {
        assertThat(AgendaImportacaoService.decidirAcao(TipoIdentificador.ID_INTERNO, false, false, true))
                .isEqualTo(AcaoPaciente.ERRO_ID_NAO_ENCONTRADO);
        // Mesmo com CPF existente e dados completos, por id interno não vincula nem cria.
        assertThat(AgendaImportacaoService.decidirAcao(TipoIdentificador.ID_INTERNO, false, true, true))
                .isEqualTo(AcaoPaciente.ERRO_ID_NAO_ENCONTRADO);
    }

    @Test
    void prontuarioOuCodigoNaoAchado_comCpfExistente_vincula() {
        assertThat(AgendaImportacaoService.decidirAcao(TipoIdentificador.PRONTUARIO, false, true, true))
                .isEqualTo(AcaoPaciente.VINCULAR_POR_CPF);
        assertThat(AgendaImportacaoService.decidirAcao(TipoIdentificador.CODIGO, false, true, false))
                .isEqualTo(AcaoPaciente.VINCULAR_POR_CPF);
    }

    @Test
    void prontuarioOuCodigoNaoAchado_semCpf_comDadosCompletos_cria() {
        assertThat(AgendaImportacaoService.decidirAcao(TipoIdentificador.PRONTUARIO, false, false, true))
                .isEqualTo(AcaoPaciente.CRIAR_NOVO);
        assertThat(AgendaImportacaoService.decidirAcao(TipoIdentificador.CODIGO, false, false, true))
                .isEqualTo(AcaoPaciente.CRIAR_NOVO);
    }

    @Test
    void prontuarioOuCodigoNaoAchado_semCpf_semDadosCompletos_erro() {
        assertThat(AgendaImportacaoService.decidirAcao(TipoIdentificador.PRONTUARIO, false, false, false))
                .isEqualTo(AcaoPaciente.ERRO_NAO_ENCONTRADO_SEM_DADOS);
    }
}
