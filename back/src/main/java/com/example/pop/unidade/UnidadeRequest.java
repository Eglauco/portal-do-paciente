package com.example.pop.unidade;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** Payload de criação/edição da unidade: dados + perfil (logo/capa/sobre/telefone/endereço) + FAQ + horários. */
public record UnidadeRequest(
        String nome,
        String codigoIntegracao,
        String logoUrl,
        String capaUrl,
        String sobre,
        String telefone,
        String endereco,
        List<FaqItem> faq,
        List<HorarioItem> horarios,
        /**
         * Minutos após a data/hora do horário para o "Aguardando confirmação" virar "Confirmação vencida".
         * 0 = desligado. null = não mexe (cliente antigo que não envia o campo; na criação vira 0).
         */
        Integer minutosConfirmacaoVencida) {

    /** Item de FAQ enviado pelo cadastro (a ordem é dada pela posição na lista). */
    public record FaqItem(String pergunta, String resposta) {
    }

    /**
     * Horário de um dia da semana. {@code fechado}: não abre. {@code vinteQuatroHoras}: aberta o dia
     * inteiro. Nos dois casos abertura/fechamento são ignorados. Hora no formato ISO "HH:mm" (ou "HH:mm:ss").
     */
    public record HorarioItem(DayOfWeek diaSemana, boolean fechado, boolean vinteQuatroHoras,
            LocalTime abertura, LocalTime fechamento) {
    }
}
