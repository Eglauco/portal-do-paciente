package com.example.pop.siresp;

/**
 * Status do ENVIO do XML ao Sistema de Gestão da unidade (o "Post XML"), persistido na coluna {@code status_envio}:
 * {@link #NAO_ENVIADO} enquanto nunca houve envio bem-sucedido (padrão / sem URL / não tentado);
 * {@link #ENVIADO} quando o Sistema de Gestão confirmou o recebimento; {@link #FALHA} quando houve tentativa e
 * o Sistema de Gestão não processou (ou falhou a conexão). É independente do status de agendamento.
 */
public enum StatusEnvio {

    NAO_ENVIADO("Não enviado"),
    ENVIADO("Enviado com sucesso"),
    FALHA("Falha no envio");

    private final String descricao;

    StatusEnvio(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
