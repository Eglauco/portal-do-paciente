package com.example.pop.unidade;

import java.util.List;

/** Retorno da unidade com o FAQ (para a tela de edição). */
public record UnidadeResponse(Long id, String nome, List<FaqItem> faq) {

    public record FaqItem(Long id, String pergunta, String resposta) {
    }

    public static UnidadeResponse from(Unidade u) {
        List<FaqItem> faq = u.getFaq().stream()
                .map(f -> new FaqItem(f.getId(), f.getPergunta(), f.getResposta()))
                .toList();
        return new UnidadeResponse(u.getId(), u.getNome(), faq);
    }
}
