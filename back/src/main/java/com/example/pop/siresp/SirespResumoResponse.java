package com.example.pop.siresp;

import java.time.LocalDateTime;

/** Linha da LISTA do SIRESP (resumo). O detalhe com todos os campos vem do GET /siresp/{id}. */
public record SirespResumoResponse(
        Long id,
        String dataAgenda,
        String horIni,
        String horFim,
        String nomePaciente,
        String cpf,
        String nomeEspecialidade,
        String nomeProfissional,
        String nomeUnidadeSolicitante,
        String arquivo,
        LocalDateTime importadoEm,
        String importadoPorNome,
        StatusSiresp status,
        String statusDescricao,
        StatusEnvio statusEnvio,
        String statusEnvioDescricao,
        TipoRegistroSiresp tipoRegistro,
        String tipoRegistroDescricao,
        TipoMovimento tipoMovimento,
        String tipoMovimentoDescricao) {

    public static SirespResumoResponse from(Siresp s) {
        StatusSiresp status = StatusSiresp.de(s);
        StatusEnvio statusEnvio = s.getStatusEnvio() == null ? StatusEnvio.NAO_ENVIADO : s.getStatusEnvio();
        TipoRegistroSiresp tipo = s.getTipoRegistro() == null ? TipoRegistroSiresp.CONSULTA : s.getTipoRegistro();
        TipoMovimento movimento = s.getTipoMovimento() == null ? TipoMovimento.AGENDAMENTO : s.getTipoMovimento();
        return new SirespResumoResponse(
                s.getId(),
                s.getDataAgenda(),
                s.getHorIni(),
                s.getHorFim(),
                s.getNomePaciente(),
                s.getCpf(),
                s.getNomeEspecialidade(),
                s.getNomeProfissional(),
                s.getNomeUnidadeSolicitante(),
                s.getArquivo(),
                s.getImportadoEm(),
                s.getImportadoPorNome(),
                status,
                status.descricao(movimento),
                statusEnvio,
                statusEnvio.getDescricao(),
                tipo,
                tipo.getDescricao(),
                movimento,
                movimento.getDescricao());
    }
}
