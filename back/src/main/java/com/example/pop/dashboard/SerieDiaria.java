package com.example.pop.dashboard;

/** Um ponto de uma série temporal diária (data ISO yyyy-MM-dd + valor). */
public record SerieDiaria(String data, long valor) {
}
