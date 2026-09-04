package com.example.pop.perfil;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro/edição de um perfil: nome + telas liberadas + unidades vinculadas. */
public record PerfilRequest(
        @NotBlank @Size(max = 120) String nome,
        Set<Tela> telas,
        List<Long> unidadeIds) {
}
