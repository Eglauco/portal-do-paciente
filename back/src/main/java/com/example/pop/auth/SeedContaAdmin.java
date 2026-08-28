package com.example.pop.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.pop.usuario.UsuarioRepository;

/**
 * Garante uma senha para a conta administrativa semeada, permitindo o primeiro login.
 * Idempotente: só define a senha se a conta existir e ainda não tiver hash.
 * Em produção, troque a senha padrão após o primeiro acesso.
 */
@Component
public class SeedContaAdmin implements ApplicationRunner {

    private static final String EMAIL_ADMIN = "adm@unidadesaude.com.br";
    private static final String SENHA_PADRAO = "Admin@123";

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public SeedContaAdmin(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.findByEmailIgnoreCase(EMAIL_ADMIN).ifPresent(usuario -> {
            if (usuario.getSenhaHash() == null || usuario.getSenhaHash().isBlank()) {
                usuario.setSenhaHash(passwordEncoder.encode(SENHA_PADRAO));
                repository.save(usuario);
            }
        });
    }
}
