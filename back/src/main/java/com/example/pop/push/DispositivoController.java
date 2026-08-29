package com.example.pop.push;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    /**
     * Registra o Expo Push Token do aparelho (idempotente). Quando a chamada traz
     * o token do paciente, vincula (ou revincula, ao trocar de paciente no mesmo
     * aparelho) o dispositivo ao dono — habilita o push direcionado.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Void> registrar(@Valid @RequestBody RegistrarDispositivoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String token = request.token().trim();
        Long pacienteId = pacienteIdDoToken(jwt);

        Dispositivo dispositivo = repository.findFirstByToken(token).orElseGet(Dispositivo::new);
        if (dispositivo.getId() == null) {
            dispositivo.setToken(token);
            dispositivo.setCriadoEm(LocalDateTime.now());
        }
        if (pacienteId != null) {
            dispositivo.setPacienteId(pacienteId);
        }
        repository.save(dispositivo);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Desvincula os aparelhos do paciente logado (chamado no logout): para de
     * receber push privado do paciente anterior num aparelho compartilhado.
     */
    @PostMapping("/desvincular")
    @Transactional
    public ResponseEntity<Void> desvincular(@AuthenticationPrincipal Jwt jwt) {
        Long pacienteId = pacienteIdDoToken(jwt);
        if (pacienteId != null) {
            List<Dispositivo> dispositivos = repository.findByPacienteId(pacienteId);
            dispositivos.forEach(d -> d.setPacienteId(null));
            repository.saveAll(dispositivos);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Long pacienteIdDoToken(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object pid = jwt.getClaim("pid");
        return pid instanceof Number numero ? numero.longValue() : null;
    }
}
