package com.example.pop.inquilino;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.ResponsavelRepository;

/**
 * Mantém os VÍNCULOS de roteamento do paciente/responsável ({@link PacienteLogin}: cpf → inquilino)
 * no schema {@code public}. Chamado no cadastro do paciente (admin) para registrar em quais clínicas
 * aquele CPF está. Fase B: um CPF pode estar em N clínicas (identidade central) — NÃO há mais a regra
 * "1 pessoa = 1 inquilino"; só adiciona o vínculo se ainda não existir para aquele inquilino.
 *
 * <p>Invariante do censo: o vínculo {@code (cpf, inquilino)} deve existir sse e só se há, no schema
 * daquele inquilino, um paciente OU um responsável com aquele CPF (alguém que loga com ele). O
 * registro é só-adição; a {@link #reconciliar} cobre a remoção (CPF trocado / responsável removido).
 */
@Service
public class RoteamentoPacienteService {

    private final PacienteLoginRepository repository;
    private final PacienteRepository pacienteRepository;
    private final ResponsavelRepository responsavelRepository;

    public RoteamentoPacienteService(PacienteLoginRepository repository,
            PacienteRepository pacienteRepository, ResponsavelRepository responsavelRepository) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
        this.responsavelRepository = responsavelRepository;
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

    /**
     * Reconcilia o censo após uma edição do cadastro: para cada CPF que PODE ter saído (os que estavam no
     * cadastro antes), remove o vínculo {@code (cpf, inquilinoId)} SE não há mais paciente NEM responsável
     * com aquele CPF no schema atual (o do inquilino). Nunca remove um vínculo ainda válido — ex.: o CPF é
     * responsável de OUTRO paciente na mesma clínica, ou virou paciente. Idempotente; CPF nulo/vazio é
     * ignorado. As checagens de existência correm no schema do {@code TenantContext} da transação chamadora,
     * então {@code inquilinoId} deve ser o do próprio schema ativo. Deve rodar DEPOIS do flush das remoções
     * (orphanRemoval / troca de CPF) para refletir o estado novo — a própria query de existência força o flush.
     */
    @Transactional
    public void reconciliar(Collection<String> cpfsCandidatos, Long inquilinoId) {
        if (inquilinoId == null || cpfsCandidatos == null) {
            return;
        }
        for (String bruto : new LinkedHashSet<>(cpfsCandidatos)) {
            if (bruto == null || bruto.isBlank()) {
                continue;
            }
            String cpf = bruto.trim();
            if (pacienteRepository.existsByCpf(cpf) || responsavelRepository.existsByCpf(cpf)) {
                continue; // ainda há quem logue com esse CPF nesta clínica → o vínculo é válido
            }
            repository.findByCpfAndInquilinoId(cpf, inquilinoId).ifPresent(repository::delete);
        }
    }
}
