package com.example.pop.perfil;

import java.util.Comparator;
import java.util.List;

import com.example.pop.common.Ref;

/** Item/detalhe de um perfil (admin). */
public record PerfilResponse(Long id, String nome, List<String> telas, List<Ref> unidades) {

    public static PerfilResponse from(Perfil p) {
        List<String> telas = p.getTelas().stream().map(Enum::name).sorted().toList();
        List<Ref> unidades = p.getUnidades().stream()
                .map(u -> new Ref(u.getId(), u.getNome()))
                .sorted(Comparator.comparing(Ref::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new PerfilResponse(p.getId(), p.getNome(), telas, unidades);
    }
}
