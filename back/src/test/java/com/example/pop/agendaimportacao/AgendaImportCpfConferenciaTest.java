package com.example.pop.agendaimportacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.pop.agendaimportacao.AgendaImportacaoService.ConferenciaCpf;

/**
 * Regra: quando o paciente é localizado pelo identificador (id/prontuário/código), o CPF informado na linha da
 * planilha tem que bater com o CPF do cadastro. Cobre os três desfechos da função pura {@code conferirCpf}.
 */
class AgendaImportCpfConferenciaTest {

    @Test
    void cpfIgual_independeDeMascara() {
        assertThat(AgendaImportacaoService.conferirCpf("52998224725", "52998224725")).isEqualTo(ConferenciaCpf.OK);
        assertThat(AgendaImportacaoService.conferirCpf("529.982.247-25", "52998224725")).isEqualTo(ConferenciaCpf.OK);
        assertThat(AgendaImportacaoService.conferirCpf("52998224725", "529.982.247-25")).isEqualTo(ConferenciaCpf.OK);
    }

    @Test
    void cpfDiferente_diverge() {
        assertThat(AgendaImportacaoService.conferirCpf("52998224725", "11144477735")).isEqualTo(ConferenciaCpf.DIVERGE);
    }

    @Test
    void cadastroSemCpf_naoTemComoConferir() {
        assertThat(AgendaImportacaoService.conferirCpf("52998224725", null)).isEqualTo(ConferenciaCpf.SEM_CPF_NO_CADASTRO);
        assertThat(AgendaImportacaoService.conferirCpf("52998224725", "")).isEqualTo(ConferenciaCpf.SEM_CPF_NO_CADASTRO);
        assertThat(AgendaImportacaoService.conferirCpf("52998224725", "   ")).isEqualTo(ConferenciaCpf.SEM_CPF_NO_CADASTRO);
    }
}
