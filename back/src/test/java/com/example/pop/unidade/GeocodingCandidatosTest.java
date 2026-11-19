package com.example.pop.unidade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tentativas de geocodificação: cidade/UF e CEP achados por padrão (o CEP não pode virar "cidade"). */
class GeocodingCandidatosTest {

    private final GeocodingService service = new GeocodingService();

    @Test
    void enderecoComBairroCidadeUfECep() {
        assertThat(service.candidatos("Av. Brasília, 2121 - Vila Sao Paulo, Araçatuba - SP, 16018-000"))
                .containsExactly(
                        "Av. Brasília, 2121 - Vila Sao Paulo, Araçatuba - SP, 16018-000",
                        "Av. Brasília, 2121 - Vila Sao Paulo, Araçatuba - SP",
                        "Brasília, Araçatuba, SP",
                        "16018-000",
                        "Araçatuba, SP");
    }

    @Test
    void enderecoSemCep() {
        assertThat(service.candidatos("Rua XV de Novembro, 50, Curitiba/PR"))
                .containsExactly(
                        "Rua XV de Novembro, 50, Curitiba/PR",
                        "XV de Novembro, Curitiba, PR",
                        "Curitiba, PR");
    }

    @Test
    void textoSoltoSemCidadeNemCep() {
        assertThat(service.candidatos("Rua Teste Chrome 123")).containsExactly("Rua Teste Chrome 123");
    }
}
