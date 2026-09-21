package com.example.pop.usoia;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

/**
 * Escritor do ledger de uso de IA: cada chamada de IA (prontuário/chat/moderação) registra UMA
 * linha imutável. É append-only; a leitura/exportação fica no {@link UsoIaController}.
 */
@Service
public class UsoIaService {

    private final UsoIaRepository repository;

    public UsoIaService(UsoIaRepository repository) {
        this.repository = repository;
    }

    /** Registra um uso de IA no ledger. Roda na transação de quem chama (commita junto do uso). */
    public void registrar(UsoIaTipo tipo, String descricao, String modelo, Long tokensEntrada, Long tokensSaida,
            BigDecimal custoUsd, String rota) {
        UsoIa u = new UsoIa();
        u.setTipo(tipo);
        u.setDescricao(recortar(descricao));
        u.setModeloIa(modelo);
        u.setTokensEntrada(tokensEntrada);
        u.setTokensSaida(tokensSaida);
        u.setCustoUsd(custoUsd);
        u.setRota(rota);
        u.setCriadoEm(LocalDateTime.now());
        repository.save(u);
    }

    /** Garante que a descrição cabe na coluna (200), sem estourar por nomes longos. */
    private static String recortar(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= 200 ? t : t.substring(0, 197) + "...";
    }
}
