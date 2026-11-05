package com.example.pop.agendaimportacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Round-trip puro (sem Spring/DB): a planilha-modelo gerada por {@link AgendaModeloPlanilha} tem que ser
 * lida de volta pelo {@link AgendaPlanilhaParser} com as colunas novas (CPF/Nome/Nome Social/Telefone/Data de
 * nascimento) mapeadas nas linhas certas. Garante o contrato entre o texto dos cabeçalhos e o parser.
 */
class AgendaImportPlanilhaRoundTripTest {

    @Test
    void modeloTemAsColunasDoPacientePorLinha() {
        byte[] xlsx = new AgendaModeloPlanilha().gerar();
        AgendaPlanilhaParser.Bruta bruta = new AgendaPlanilhaParser().parse(xlsx);

        // Cabeçalho da agenda ainda é lido.
        assertThat(bruta.agenda()).containsEntry("data", "25/03/2026");

        // As 2 linhas de exemplo com os dados do paciente por linha.
        List<AgendaPlanilhaParser.LinhaBruta> horarios = bruta.horarios();
        assertThat(horarios).hasSize(2);

        AgendaPlanilhaParser.LinhaBruta l1 = horarios.get(0);
        assertThat(l1.pacienteProntuario()).isEqualTo("1001");
        assertThat(l1.cpf()).isEqualTo("529.982.247-25");
        assertThat(l1.nome()).isEqualTo("Maria de Souza");
        assertThat(l1.nomeSocial()).isBlank();
        assertThat(l1.telefone()).isEqualTo("(11) 98888-7777");
        assertThat(l1.dataNascimento()).isEqualTo("15/04/1990");
        assertThat(l1.inicio()).isEqualTo("08:00");

        AgendaPlanilhaParser.LinhaBruta l2 = horarios.get(1);
        assertThat(l2.pacienteCodigo()).isEqualTo("P-2002");
        assertThat(l2.cpf()).isEqualTo("111.444.777-35");
        assertThat(l2.nome()).isEqualTo("João Lima");
        assertThat(l2.nomeSocial()).isEqualTo("Joana Lima");
        assertThat(l2.telefone()).isEqualTo("(11) 97777-6666");
        assertThat(l2.dataNascimento()).isEqualTo("20/09/1985");
    }
}
