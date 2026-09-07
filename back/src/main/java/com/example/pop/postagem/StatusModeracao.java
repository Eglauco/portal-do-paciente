package com.example.pop.postagem;

/**
 * Estado de moderação de um comentário quando a postagem tem "validar por IA" ligado.
 * PUBLICADO: visível para todos (default; ou aprovado pelo admin). PENDENTE: potencialmente
 * ofensivo (ou não validado) — oculto do público, aguardando o admin. REJEITADO: o admin
 * decidiu não publicar (segue oculto; preservado para auditoria).
 */
public enum StatusModeracao {
    PUBLICADO,
    PENDENTE,
    REJEITADO
}
