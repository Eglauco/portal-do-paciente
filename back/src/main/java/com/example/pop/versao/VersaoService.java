package com.example.pop.versao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Lê a versão do BACK do registro (resources/versao.properties, carregado por {@link VersaoConfig})
 * e monta o contrato público. Imutável: lida uma vez no boot.
 */
@Service
public class VersaoService {

    private final VersaoResponse versao;

    public VersaoService(
            @Value("${versao.back.numero:0.0.0}") String numero,
            @Value("${versao.back.data:-}") String data) {
        this.versao = new VersaoResponse(numero, data);
    }

    /** Versão atual do backend (número + data ISO). */
    public VersaoResponse versao() {
        return versao;
    }
}
