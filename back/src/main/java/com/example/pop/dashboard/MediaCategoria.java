package com.example.pop.dashboard;

/** Média (1–5) de uma categoria de NPS e quantas respostas a compõem. */
public record MediaCategoria(String categoria, double media, long respostas) {
}
