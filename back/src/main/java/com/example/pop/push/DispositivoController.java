package com.example.pop.push;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/dispositivo")
public class DispositivoController {

    private final DispositivoRepository repository;

    public DispositivoController(DispositivoRepository repository) {
        this.repository = repository;
    }

    /** Registra o Expo Push Token do aparelho (idempotente). */
    @PostMapping
    @Transactional
    public ResponseEntity<Void> registrar(@Valid @RequestBody RegistrarDispositivoRequest request) {
        String token = request.token().trim();
        if (!repository.existsByToken(token)) {
            Dispositivo dispositivo = new Dispositivo();
            dispositivo.setToken(token);
            dispositivo.setCriadoEm(LocalDateTime.now());
            repository.save(dispositivo);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
