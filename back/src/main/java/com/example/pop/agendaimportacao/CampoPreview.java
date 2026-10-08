package com.example.pop.agendaimportacao;

/**
 * Uma célula do preview da importação de agenda: o valor que o cliente digitou na planilha, o valor
 * resolvido (o nome do cadastro casado por nome/código, ou a forma normalizada da data/hora/CPF), e se
 * há erro + a mensagem. O front mostra {@code valor} e destaca a célula quando {@code erro} é {@code true},
 * exibindo {@code mensagem}. Quando {@code valor} vem vazio e {@code resolvido} está preenchido, é um valor
 * assumido por padrão (ex.: status).
 *
 * <p>Fase 1 (preview): NADA é persistido — este DTO só diz ao cliente o que está certo e o que corrigir.
 */
public record CampoPreview(String valor, String resolvido, boolean erro, String mensagem) {

    public static CampoPreview ok(String valor) {
        return new CampoPreview(valor == null ? "" : valor, null, false, null);
    }

    public static CampoPreview ok(String valor, String resolvido) {
        return new CampoPreview(valor == null ? "" : valor, resolvido, false, null);
    }

    public static CampoPreview erro(String valor, String mensagem) {
        return new CampoPreview(valor == null ? "" : valor, null, true, mensagem);
    }
}
