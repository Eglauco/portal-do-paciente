package com.example.pop.push;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Long contaId = claimLong(jwt, "cid");
        Long pacienteId = claimLong(jwt, "pid");

        Dispositivo dispositivo = repository.findFirstByToken(token).orElseGet(Dispositivo::new);
        if (dispositivo.getId() == null) {
            dispositivo.setToken(token);
            dispositivo.setCriadoEm(LocalDateTime.now());
        }
        // O aparelho pertence à CONTA; paciente_id fica só como legado/transição.
        if (contaId != null) {
            dispositivo.setContaId(contaId);
        }
        if (pacienteId != null) {
            dispositivo.setPacienteId(pacienteId);
        }
        repository.save(dispositivo);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Desvincula os aparelhos da conta logada (chamado no logout): para de receber
     * push privado da conta anterior num aparelho compartilhado.
     */
    @PostMapping("/desvincular")
    @Transactional
    public ResponseEntity<Void> desvincular(@AuthenticationPrincipal Jwt jwt) {
        Long contaId = claimLong(jwt, "cid");
        Long pacienteId = claimLong(jwt, "pid");
        Map<Long, Dispositivo> alvos = new LinkedHashMap<>();
        if (contaId != null) {
            repository.findByContaId(contaId).forEach(d -> alvos.put(d.getId(), d));
        }
        if (pacienteId != null) {
            // Só aparelhos legados (sem conta): evita zerar o aparelho de outra conta.
            repository.findByPacienteIdAndContaIdIsNull(pacienteId).forEach(d -> alvos.put(d.getId(), d));
        }
        alvos.values().forEach(d -> {
            d.setContaId(null);
            d.setPacienteId(null);
        });
        repository.saveAll(alvos.values());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static Long claimLong(Jwt jwt, String claim) {
        if (jwt == null) {
            return null;
        }
        Object valor = jwt.getClaim(claim);
        return valor instanceof Number numero ? numero.longValue() : null;
    }
}
