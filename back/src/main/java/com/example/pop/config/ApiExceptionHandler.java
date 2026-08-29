package com.example.pop.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
