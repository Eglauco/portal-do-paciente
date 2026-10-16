package com.example.pop.push;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Token de ENTREGA assinado (HMAC-SHA256), curto e de escopo ÚNICO: autoriza apenas
 * marcar como entregues (2º "check") as mensagens da unidade de um paciente num inquilino.
 *
 * <p>Vai dentro do push, em {@code data.ack}. A Notification Service Extension do iOS e a
 * background task do Android o ecoam para {@code POST /push/entregue} com o app FECHADO,
 * SEM precisar do token de sessão do paciente (que é longevo e abre todo o {@code /meu/**}).
 *
 * <p>Formato compacto (cabe no limite ~4KB do APNs/FCM): {@code base64url(payload).base64url(hmac)},
 * com {@code payload = "inq:pid:exp"} (exp em epoch-seconds). Segredo DEDICADO
 * ({@code app.push-ack.secret}), separado do {@code app.jwt.secret}: um ack vazado nunca vira sessão,
 * e só consegue (re)marcar entrega — ação idempotente e sem leitura de dados.
 */
@Component
public class PushAckToken {

    private static final Logger log = LoggerFactory.getLogger(PushAckToken.class);
    /** Validade curta: cobre a latência do push e limita a janela de replay. */
    private static final long VALIDADE_SEGUNDOS = 15 * 60;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecretKeySpec chave;

    public PushAckToken(@Value("${app.push-ack.secret}") String segredo) {
        this.chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Conteúdo verificado do token (inquilino pode ser nulo = schema padrão). */
    public record Conteudo(Long inquilinoId, Long pacienteId) {
    }

    /** Cunha um token para confirmar a entrega do paciente no inquilino (validade curta). */
    public String gerar(Long inquilinoId, Long pacienteId) {
        long exp = Instant.now().getEpochSecond() + VALIDADE_SEGUNDOS;
        String payload = inquilinoId + ":" + pacienteId + ":" + exp;
        String p = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + B64.encodeToString(hmac(p));
    }

    /** Verifica a assinatura e a expiração; devolve o conteúdo, ou {@code null} se inválido/expirado. */
    public Conteudo verificar(String token) {
        if (token == null) {
            return null;
        }
        int ponto = token.indexOf('.');
        if (ponto <= 0 || ponto == token.length() - 1) {
            return null;
        }
        String p = token.substring(0, ponto);
        String sig = token.substring(ponto + 1);
        try {
            // Comparação em tempo constante (evita timing attack na assinatura).
            if (!MessageDigest.isEqual(hmac(p), B64D.decode(sig))) {
                return null;
            }
            String[] partes = new String(B64D.decode(p), StandardCharsets.UTF_8).split(":");
            if (partes.length != 3) {
                return null;
            }
            if (Instant.now().getEpochSecond() > Long.parseLong(partes[2])) {
                return null; // expirado
            }
            Long inq = "null".equals(partes[0]) ? null : Long.valueOf(partes[0]);
            return new Conteudo(inq, Long.valueOf(partes[1]));
        } catch (RuntimeException e) {
            log.debug("Token de entrega inválido: {}", e.getMessage());
            return null;
        }
    }

    private byte[] hmac(String dados) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chave);
            return mac.doFinal(dados.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar token de entrega", e);
        }
    }
}
