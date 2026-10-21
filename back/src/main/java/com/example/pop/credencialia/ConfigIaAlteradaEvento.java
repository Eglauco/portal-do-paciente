package com.example.pop.credencialia;

/** Disparado após salvar/remover uma credencial de IA, para invalidar os caches do schema no commit. */
public record ConfigIaAlteradaEvento(String schema) {
}
