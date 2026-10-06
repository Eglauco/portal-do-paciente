package com.example.pop.procedimento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro/edição de um documento TCLE do procedimento (admin).
 *
 * <p>Duas origens (o controller valida conforme {@code origemModelo}):
 * <ul>
 *   <li>{@code ARQUIVO} (padrão): o .docx é enviado direto ao S3 e aqui chega a {@code url} + content-type.</li>
 *   <li>{@code ZAPSIGN_MODELO}: sem arquivo nosso; chega o {@code providerTemplateToken} (modelo escolhido no
 *       ZapSign) e, para exibição, o {@code modeloProviderNome}.</li>
 * </ul>
 */
public record TermoProcedimentoRequest(
        @NotBlank @Size(max = 120) String nome,
        OrigemModeloTermo origemModelo,
        String url,
        @Size(max = 120) String contentType,
        @Size(max = 120) String providerTemplateToken,
        @Size(max = 200) String modeloProviderNome,
        /** Se true, o profissional de saúde do atendimento também assina (após o paciente). Boolean p/ aceitar ausente. */
        Boolean profissionalAssina,
        /** Só com profissionalAssina: se true, o profissional assina com certificado digital (ICP), senão em tela. */
        Boolean profissionalCertificado) {

    /** Origem efetiva (padrão ARQUIVO quando o cliente não manda). */
    public OrigemModeloTermo origemEfetiva() {
        return origemModelo == null ? OrigemModeloTermo.ARQUIVO : origemModelo;
    }

    /** Coassinatura efetiva (padrão false quando ausente). */
    public boolean profissionalAssinaEfetivo() {
        return Boolean.TRUE.equals(profissionalAssina);
    }

    /** Certificado efetivo: só quando a coassinatura está ligada e o cliente pediu certificado. */
    public boolean profissionalCertificadoEfetivo() {
        return profissionalAssinaEfetivo() && Boolean.TRUE.equals(profissionalCertificado);
    }
}
