package com.example.pop.prontuario;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Um atendimento com termos aguardando a COASSINATURA do profissional logado. Agrupa os termos da mesma
 * cerimônia (compartilham a {@code signUrl} do profissional) para o front abrir UMA cerimônia embutida.
 * {@code abrirEmAba} = a cerimônia deve abrir em ABA nova em vez de {@code <iframe>} (ex.: DocuSign, certificado).
 */
public record TermoProfissionalResponse(Long prontuarioId, String paciente, String especialidade,
        LocalDateTime dataHora, String signUrl, boolean certificado, boolean abrirEmAba, List<String> termos) {
}
