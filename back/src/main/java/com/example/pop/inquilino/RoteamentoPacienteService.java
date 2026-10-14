package com.example.pop.inquilino;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Garante o vínculo {@code (cpf, inquilinoId)}. Idempotente E à prova de corrida: delega ao
     * {@code INSERT ... ON CONFLICT DO NOTHING} (o banco resolve o duplo-insert concorrente sem lançar
     * exceção), em vez de find-then-save — que sob dois cadastros/edições simultâneos do mesmo CPF novo
     * estouraria a violação do índice único só no commit e derrubaria a transação do cadastro inteira.
     * Cria mesmo que o CPF já tenha vínculo com OUTRAS clínicas. CPF nulo/vazio é ignorado
     * (responsável sem CPF não loga).
     */
    @Transactional
    public void registrar(String cpf, Long inquilinoId) {
        if (cpf == null || cpf.isBlank() || inquilinoId == null) {
            return;
        }
        // O INSERT nativo (@Modifying) EXIGE uma transação ativa — ao contrário do save() do repositório,
        // que abria a sua. Com propagação REQUIRED: junta-se à transação do cadastro (criar/atualizar) quando
        // há uma, e abre a própria nos fluxos de auth (ativar/login/trocar-clínica), que não são @Transactional.
        repository.inserirSeAusente(cpf, inquilinoId);
    }
}
