package com.example.pop.dashboard;

/** Item de um ranking/contagem simples (rótulo + valor), ex.: top procedimentos. */
public record ItemContagem(String rotulo, long valor) {
}
