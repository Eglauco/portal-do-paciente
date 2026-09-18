package com.example.pop.pacienteauth;

/**
 * Resposta do pedido de código: o telefone (mascarado) para onde o OTP foi enviado.
 * Como o usuário informa o CPF (não o telefone), o app precisa disto para mostrar
 * "enviamos o código para (••) •••••-1234".
 */
public record SolicitarCodigoResponse(String telefoneMascarado) {
}
