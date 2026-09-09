package com.example.pop.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rede de segurança para violações de integridade que não foram tratadas localmente:
 * um índice único disparado (ex.: reenvio concorrente da mesma mensagem com o mesmo
 * clienteId) vira 409 em vez de 500. O cliente reenvia e o próximo request deduplica.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Void> integridade(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    /**
     * Devolve o motivo ({@code reason}) das {@link ResponseStatusException} no corpo JSON
     * ({@code message}), para o app/front mostrarem a causa ao usuário (ex.: idade mínima
     * para comentar). Preferido a {@code server.error.include-message=always}: expõe apenas
     * as mensagens autorais (voltadas ao usuário), sem vazar o {@code message} de exceções
     * de framework/não-tratadas (JSON malformado, NPE etc.), que seguem sem mensagem.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> statusException(ResponseStatusException e) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("status", e.getStatusCode().value());
        corpo.put("message", e.getReason() == null ? "" : e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(corpo);
    }
}
