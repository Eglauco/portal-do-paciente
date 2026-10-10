package com.example.pop.inquilino;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gate do SUPER-ADMIN: confere o segredo fixo do header {@code X-SuperAdmin-Secret} em tempo constante.
 * Compartilhado por todos os endpoints {@code /superadmin/**} (gestão de inquilinos + config de
 * plataforma), que são {@code permitAll} no {@code SecurityConfig} e protegidos AQUI, no controller.
 */
@Component
public class SuperAdminAutenticacao {

    private final byte[] segredo;

    public SuperAdminAutenticacao(@Value("${app.superadmin.secret}") String segredo) {
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    /** Confere o segredo; lança 401 se ausente/errado. */
    public void conferir(String secret) {
        byte[] enviado = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(segredo, enviado)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial de super-admin inválida.");
        }
    }
}
