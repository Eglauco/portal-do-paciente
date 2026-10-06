package com.example.pop.unidade;

import java.util.List;

/** Payload de criação/edição da unidade, incluindo o FAQ (aba do cadastro). */
public record UnidadeRequest(String nome, String codigoIntegracao, List<FaqItem> faq) {

    /** Item de FAQ enviado pelo cadastro (a ordem é dada pela posição na lista). */
    public record FaqItem(String pergunta, String resposta) {
    }
}
