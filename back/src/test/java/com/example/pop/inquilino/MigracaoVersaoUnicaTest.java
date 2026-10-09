package com.example.pop.inquilino;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Guard do split Flyway (plataforma × tenant): a numeração das migrations é um contador GLOBAL único
 * entre as DUAS trilhas — {@code db/migration} (tenant) e {@code db/platform} (plataforma). O app
 * principal (public) une as duas locations; se o MESMO número {@code V<n>} aparecer nas duas pastas, o
 * Flyway aborta o boot com "Found more than one migration with version <n>" e o sistema não sobe.
 *
 * <p>Como {@code db/migration} termina em V147 com um "buraco" em 148-150 (que vivem em
 * {@code db/platform}), é fácil alguém numerar a próxima migration de tenant como V148 e colidir. Este
 * teste trava isso no CI/code-review: toda migration nova (de qualquer trilha) deve ser numerada ACIMA
 * do maior V global existente. Teste puro de filesystem (sem Spring/DB).
 */
class MigracaoVersaoUnicaTest {

    private static final Pattern VERSAO = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    void versoesNaoColidemEntreAsTrilhasFlyway() {
        Map<Integer, List<String>> porVersao = new TreeMap<>();
        coletar(new File("src/main/resources/db/migration"), porVersao);
        coletar(new File("src/main/resources/db/platform"), porVersao);

        List<String> colisoes = porVersao.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> "V" + e.getKey() + " em " + e.getValue())
                .toList();

        assertTrue(colisoes.isEmpty(),
                "Versões de migration repetidas entre as trilhas Flyway (numere a nova migration ACIMA do "
                        + "maior V global): " + colisoes);
    }

    private static void coletar(File dir, Map<Integer, List<String>> porVersao) {
        File[] arquivos = dir.listFiles();
        assertTrue(arquivos != null, "pasta de migrations não encontrada: " + dir.getPath());
        for (File f : arquivos) {
            Matcher m = VERSAO.matcher(f.getName());
            if (m.matches()) {
                porVersao.computeIfAbsent(Integer.valueOf(m.group(1)), k -> new ArrayList<>())
                        .add(dir.getName() + "/" + f.getName());
            }
        }
    }
}
