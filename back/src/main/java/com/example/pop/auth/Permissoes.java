package com.example.pop.auth;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.pop.common.Ref;
import com.example.pop.perfil.Perfil;
import com.example.pop.unidade.Unidade;
import com.example.pop.usuario.Usuario;

/**
 * Permissão efetiva de um usuário = UNIÃO dos seus perfis: telas liberadas +
 * unidades acessíveis. A unidade ativa é sempre uma das acessíveis.
 */
public final class Permissoes {

    private Permissoes() {
    }

    /** Telas liberadas (nomes do enum), sem repetir, em ordem. */
    public static List<String> telas(Usuario u) {
        return u.getPerfis().stream()
                .flatMap(p -> p.getTelas().stream())
                .map(Enum::name)
                .distinct()
                .sorted()
                .toList();
    }

    /** Unidades acessíveis (união), sem duplicar, ordenadas por nome. */
    public static List<Ref> unidades(Usuario u) {
        Map<Long, String> nomes = new LinkedHashMap<>();
        for (Perfil p : u.getPerfis()) {
            for (Unidade un : p.getUnidades()) {
                nomes.putIfAbsent(un.getId(), un.getNome());
            }
        }
        return nomes.entrySet().stream()
                .map(e -> new Ref(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(Ref::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Unidade ativa efetiva: a atual se acessível; senão a primeira acessível (ou null). */
    public static Unidade unidadeAtivaEfetiva(Usuario u) {
        Set<Long> acessiveis = idsAcessiveis(u);
        Unidade atual = u.getUnidade();
        if (atual != null && acessiveis.contains(atual.getId())) {
            return atual;
        }
        return u.getPerfis().stream()
                .flatMap(p -> p.getUnidades().stream())
                .min(Comparator.comparing(Unidade::getNome, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    public static boolean podeAcessarUnidade(Usuario u, Long unidadeId) {
        return idsAcessiveis(u).contains(unidadeId);
    }

    private static Set<Long> idsAcessiveis(Usuario u) {
        return u.getPerfis().stream()
                .flatMap(p -> p.getUnidades().stream())
                .map(Unidade::getId)
                .collect(Collectors.toSet());
    }
}
