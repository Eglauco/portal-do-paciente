package com.example.pop.configuracao;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra/decifra os valores de configuração do tipo {@link TipoConfiguracao#SEGREDO} (tokens dos provedores de
 * assinatura) com AES-256-GCM, usando a chave-mestra de {@code app.secrets.key} (env {@code APP_SECRETS_KEY},
 * 32 bytes em base64). Formato guardado: {@code base64( IV(12) || ciphertext+tag )} — IV aleatório por valor.
 *
 * <p>Sem a chave-mestra (ausente/inválida) as operações LANÇAM (fail-safe): preferimos recusar a operar com
 * segredo sem proteção. A chave NUNCA deve ser trocada depois de haver segredos salvos (os valores já cifrados
 * ficariam ilegíveis) — rotação com recifragem é um item futuro.
 */
@Component
public class SegredoCripto {

    private static final int TAM_IV = 12;         // nonce padrão do GCM
    private static final int TAM_TAG_BITS = 128;  // tag de autenticação do GCM
    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";

    /** null quando a chave-mestra não está configurada ou é inválida. */
    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public SegredoCripto(@Value("${app.secrets.key:}") String chaveBase64) {
        this.chave = carregar(chaveBase64);
    }

    /** true se a chave-mestra está configurada (senão cifrar/decifrar lançam). */
    public boolean disponivel() {
        return chave != null;
    }

    /** Cifra o texto puro → {@code base64(IV||ciphertext)}. Null/vazio → null. */
    public String cifrar(String plano) {
        if (plano == null || plano.isEmpty()) {
            return null;
        }
        exigirChave();
        try {
            byte[] iv = new byte[TAM_IV];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(TRANSFORMACAO);
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plano.getBytes(StandardCharsets.UTF_8));
            byte[] saida = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, saida, 0, iv.length);
            System.arraycopy(ct, 0, saida, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(saida);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar segredo.", e);
        }
    }

    /** Decifra {@code base64(IV||ciphertext)} → texto puro. Null/vazio → null. */
    public String decifrar(String cifrado) {
        if (cifrado == null || cifrado.isEmpty()) {
            return null;
        }
        exigirChave();
        try {
            byte[] bruto = Base64.getDecoder().decode(cifrado);
            Cipher c = Cipher.getInstance(TRANSFORMACAO);
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAM_TAG_BITS, bruto, 0, TAM_IV));
            byte[] pt = c.doFinal(bruto, TAM_IV, bruto.length - TAM_IV);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decifrar segredo (chave-mestra trocada/ inválida?).", e);
        }
    }

    private void exigirChave() {
        if (chave == null) {
            throw new IllegalStateException(
                    "app.secrets.key não configurada — não é possível proteger/ler os segredos dos provedores.");
        }
    }

    /** Decodifica a chave-mestra base64; exige 16/24/32 bytes (AES-128/192/256). Inválida → null. */
    private static SecretKeySpec carregar(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] k = Base64.getDecoder().decode(base64.trim());
            if (k.length != 16 && k.length != 24 && k.length != 32) {
                return null;
            }
            return new SecretKeySpec(k, "AES");
        } catch (Exception e) {
            return null;
        }
    }
}
