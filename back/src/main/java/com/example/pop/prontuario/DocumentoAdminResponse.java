package com.example.pop.prontuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Documento visto pelo BACK-OFFICE, com o resultado da análise por IA (resumo clínico, status,
 * validação). NÃO é exposto ao app do paciente (o resumo clínico é só para a unidade).
 */
public record DocumentoAdminResponse(
        Long id,
        String nome,
        String url,
        Long tipoId,
        String tipoNome,
        String resumoClinico,
        StatusAnaliseDocumento statusAnalise,
        String statusAnaliseDescricao,
        String validadoPorNome,
        LocalDateTime validadoEm,
        String observacaoValidacao,
        LocalDateTime analisadoEm,
        Long tokensEntrada,
        Long tokensSaida,
        String modeloIa,
        BigDecimal custoUsd,
        Integer geracoesIa) {

    public static DocumentoAdminResponse from(Documento d) {
        return new DocumentoAdminResponse(
                d.getId(),
                d.getNome(),
                d.getUrl(),
                d.getTipo() == null ? null : d.getTipo().getId(),
                d.getTipo() == null ? null : d.getTipo().getNome(),
                d.getResumoClinico(),
                d.getStatusAnalise(),
                d.getStatusAnalise() == null ? null : d.getStatusAnalise().getDescricao(),
                d.getValidadoPor() == null ? null : d.getValidadoPor().getNome(),
                d.getValidadoEm(),
                d.getObservacaoValidacao(),
                d.getAnalisadoEm(),
                d.getTokensEntrada(),
                d.getTokensSaida(),
                d.getModeloIa(),
                d.getCustoUsd(),
                d.getGeracoesIa());
    }
}
