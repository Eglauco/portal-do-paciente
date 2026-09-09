package com.example.pop.configuracao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Configuração exibida no CRUD (lista e edição). */
public record ConfiguracaoResponse(
        Long id,
        String nome,
        String descricao,
        String chave,
        TipoConfiguracao tipoConfiguracao,
        Boolean valorBooleano,
        String valorTexto,
        BigDecimal valorNumerico,
        String valorCor,
        LocalDateTime atualizadoEm,
        /** Nome do admin que fez a última alteração (auditoria); null se nunca editado. */
        String atualizadoPorNome) {

    public static ConfiguracaoResponse from(Configuracao c, String atualizadoPorNome) {
        return new ConfiguracaoResponse(c.getId(), c.getNome(), c.getDescricao(), c.getChave(),
                c.getTipoConfiguracao(), c.getValorBooleano(), c.getValorTexto(), c.getValorNumerico(),
                c.getValorCor(), c.getAtualizadoEm(), atualizadoPorNome);
    }
}
