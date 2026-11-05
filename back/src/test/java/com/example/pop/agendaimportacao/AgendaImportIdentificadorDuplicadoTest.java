package com.example.pop.agendaimportacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.pop.agendaimportacao.AgendaImportacaoService.ChaveCriacao;

/**
 * Checagem pura: entre linhas que vão CRIAR paciente, o mesmo prontuário/código novo com CPFs diferentes é
 * conflito (geraria dois pacientes com o mesmo identificador → viola o índice único). Mesmo CPF = mesma pessoa.
 */
class AgendaImportIdentificadorDuplicadoTest {

    @Test
    void mesmoProntuarioNovo_cpfsDiferentes_conflita() {
        List<ChaveCriacao> chaves = List.of(
                new ChaveCriacao(true, "P1", null, "111"),
                new ChaveCriacao(true, "P1", null, "222"));
        assertThat(AgendaImportacaoService.indicesComIdentificadorNovoDuplicado(chaves)).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void mesmoProntuarioNovo_mesmoCpf_naoConflita() {
        List<ChaveCriacao> chaves = List.of(
                new ChaveCriacao(true, "P1", null, "111"),
                new ChaveCriacao(true, "P1", null, "111"));
        assertThat(AgendaImportacaoService.indicesComIdentificadorNovoDuplicado(chaves)).isEmpty();
    }

    @Test
    void prontuariosNovosDiferentes_naoConflita() {
        List<ChaveCriacao> chaves = List.of(
                new ChaveCriacao(true, "P1", null, "111"),
                new ChaveCriacao(true, "P2", null, "222"));
        assertThat(AgendaImportacaoService.indicesComIdentificadorNovoDuplicado(chaves)).isEmpty();
    }

    @Test
    void mesmoCodigoNovo_cpfsDiferentes_conflita() {
        List<ChaveCriacao> chaves = List.of(
                new ChaveCriacao(true, null, "C9", "111"),
                new ChaveCriacao(true, null, "C9", "222"));
        assertThat(AgendaImportacaoService.indicesComIdentificadorNovoDuplicado(chaves)).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void linhasQueNaoCriam_saoIgnoradas() {
        // Mesmo prontuário/CPF diferente, mas são linhas de paciente EXISTENTE (criar=false): não conflita.
        List<ChaveCriacao> chaves = List.of(
                new ChaveCriacao(false, "P1", null, "111"),
                new ChaveCriacao(false, "P1", null, "222"));
        assertThat(AgendaImportacaoService.indicesComIdentificadorNovoDuplicado(chaves)).isEmpty();
    }

    @Test
    void sinalizaApenasAsLinhasEmConflito() {
        List<ChaveCriacao> chaves = List.of(
                new ChaveCriacao(true, "P1", null, "111"),
                new ChaveCriacao(true, "P2", null, "999"),
                new ChaveCriacao(true, "P1", null, "222"));
        assertThat(AgendaImportacaoService.indicesComIdentificadorNovoDuplicado(chaves)).containsExactlyInAnyOrder(0, 2);
    }
}
