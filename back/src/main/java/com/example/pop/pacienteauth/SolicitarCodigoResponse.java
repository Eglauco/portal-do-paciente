package com.example.pop.pacienteauth;

import java.util.List;

/**
 * Resposta do pedido de código: o telefone (mascarado) para onde o OTP foi enviado + as clínicas em que a
 * identidade (CPF + data + telefone) confere. Como o usuário informa o CPF (não o telefone), o app precisa do
 * telefone mascarado para mostrar "enviamos o código para (••) •••••-1234". As {@code clinicas} servem ao app
 * COMPARTILHADO: 1 → segue direto para o código; N → "Selecionar Clínica" antes de ativar (o OTP é de uso único,
 * então a clínica é escolhida ANTES do {@code ativar}). No app A-baked vem só a clínica assada.
 */
public record SolicitarCodigoResponse(String telefoneMascarado, List<ClinicaLogin> clinicas) {
}
