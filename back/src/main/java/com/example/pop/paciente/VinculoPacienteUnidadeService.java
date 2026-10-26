package com.example.pop.paciente;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Garante que o paciente tenha a unidade em "Unidades de acesso" (tabela {@code paciente_unidade}).
 *
 * <p>Chamado ao <b>criar conversa ou agendamento</b> entre o paciente e a unidade, para que ele
 * passe a enxergar o perfil da unidade (tela "Dados da unidade"), o feed, o chat e o SAU dela. Sem
 * isso, o app lista a conversa (filtrada só por paciente) mas {@code GET /meu/unidades/{id}} devolve
 * 403 — o paciente conversa com a unidade sem estar vinculado a ela.
 *
 * <p>Idempotente: no-op quando já vinculado, quando algum id é nulo ou quando paciente/unidade não
 * existe. Carrega ambos na própria transação (evita mexer em entidade detached) e só grava quando o
 * vínculo é novo.
 */
@Service
public class VinculoPacienteUnidadeService {

    private final PacienteRepository pacienteRepository;
    private final UnidadeRepository unidadeRepository;

    public VinculoPacienteUnidadeService(PacienteRepository pacienteRepository,
            UnidadeRepository unidadeRepository) {
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
    }

    /** Vincula (idempotente) a unidade ao paciente. */
    @Transactional
    public void garantir(Long pacienteId, Long unidadeId) {
        if (pacienteId == null || unidadeId == null) {
            return;
        }
        Paciente paciente = pacienteRepository.findById(pacienteId).orElse(null);
        if (paciente == null) {
            return;
        }
        Unidade unidade = unidadeRepository.findById(unidadeId).orElse(null);
        if (unidade == null) {
            return;
        }
        if (paciente.getUnidades().add(unidade)) {
            pacienteRepository.save(paciente);
        }
    }
}
