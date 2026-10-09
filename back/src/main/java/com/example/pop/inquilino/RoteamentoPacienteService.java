package com.example.pop.inquilino;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Mantém o ponteiro de roteamento do paciente/responsável ({@link PacienteLogin}: cpf → inquilino)
 * no schema {@code public}. Chamado no cadastro do paciente (admin) para que o login do app ache o
 * inquilino pelo CPF. Idempotente; aplica a regra "1 pessoa = 1 inquilino" (erro se o CPF já é de
 * outro inquilino).
 */
@Service
public class RoteamentoPacienteService {

    private final PacienteLoginRepository repository;

    public RoteamentoPacienteService(PacienteLoginRepository repository) {
        this.repository = repository;
    }

    /**
     * Garante o ponteiro {@code cpf → inquilinoId}. Se o CPF já aponta para o MESMO inquilino, não faz
     * nada (idempotente). Se aponta para OUTRO inquilino, erro 409 (1 pessoa = 1 inquilino). CPF
     * nulo/vazio é ignorado (responsável sem CPF não loga).
     */
    public void registrar(String cpf, Long inquilinoId) {
        if (cpf == null || cpf.isBlank() || inquilinoId == null) {
            return;
        }
        repository.findByCpf(cpf).ifPresentOrElse(existente -> {
            if (!inquilinoId.equals(existente.getInquilinoId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O CPF " + cpf + " já está cadastrado em outro inquilino.");
            }
        }, () -> {
            PacienteLogin login = new PacienteLogin();
            login.setCpf(cpf);
            login.setInquilinoId(inquilinoId);
            repository.save(login);
        });
    }
}
