package com.example.pop.configuracaoagenda;

/**
 * De onde vem o modelo do termo (TCLE):
 * <ul>
 *   <li>{@code ARQUIVO} — o cliente envia o .docx nosso (fica no S3). Funciona em QUALQUER provedor: a
 *       ZapSign registra como Modelo; os demais (Autentique/Clicksign/DocuSign) renderizam o .docx local.</li>
 *   <li>{@code ZAPSIGN_MODELO} — o cliente seleciona um MODELO já pronto no ZapSign (personalizado lá). Não
 *       há .docx nosso; enviamos só as variáveis. Por ser específico do ZapSign, SÓ o ZapSign assina esse termo.</li>
 * </ul>
 */
public enum OrigemModeloTermo {
    ARQUIVO,
    ZAPSIGN_MODELO
}
