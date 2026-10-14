package com.example.pop.inquilino;

import org.springframework.stereotype.Service;

/**
 * Mantém os VÍNCULOS de roteamento do paciente/responsável ({@link PacienteLogin}: cpf → inquilino)
 * no schema {@code public}. Chamado no cadastro do paciente (admin) para registrar em quais clínicas
 * aquele CPF está. Fase B: um CPF pode estar em N clínicas (identidade central) — NÃO há mais a regra
 * "1 pessoa = 1 inquilino"; só adiciona o vínculo se ainda não existir para aquele inquilino.
 */
@Service
public class RoteamentoPacienteService {

    private final PacienteLoginRepository repository;

    public RoteamentoPacienteService(PacienteLoginRepository repository) {
        this.repository = repository;
    }

    /**
     * Garante o vínculo {@code (cpf, inquilinoId)}. Idempotente: se já existe para aquele inquilino, não
     * faz nada; se não existe, cria (mesmo que o CPF já tenha vínculo com OUTRAS clínicas). CPF nulo/vazio
     * é ignorado (responsável sem CPF não loga).
     */
    public void registrar(String cpf, Long inquilinoId) {
        if (cpf == null || cpf.isBlank() || inquilinoId == null) {
            return;
        }
        if (repository.findByCpfAndInquilinoId(cpf, inquilinoId).isPresent()) {
            return; // vínculo já existe para este inquilino
        }
        PacienteLogin login = new PacienteLogin();
        login.setCpf(cpf);
        login.setInquilinoId(inquilinoId);
        repository.save(login);
    }
}
