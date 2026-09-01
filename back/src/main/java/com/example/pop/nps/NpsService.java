package com.example.pop.nps;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.categorianps.CategoriaNps;
import com.example.pop.categorianps.CategoriaNpsRepository;
import com.example.pop.push.PushService;

@Service
public class NpsService {

    private final NpsRepository repository;
    private final CategoriaNpsRepository categoriaRepository;
    private final PushService pushService;

    public NpsService(NpsRepository repository, CategoriaNpsRepository categoriaRepository, PushService pushService) {
        this.repository = repository;
        this.categoriaRepository = categoriaRepository;
        this.pushService = pushService;
    }

    /**
     * Gera um NPS (pendente) vinculado ao agendamento quando o status for
     * PRESENCA_PACIENTE e ainda não existir um NPS para ele.
     */
    public void gerarSeNecessario(Agendamento agendamento) {
        if (agendamento.getStatusAgendamento() != StatusAgendamento.PRESENCA_PACIENTE) {
            return;
        }
        if (repository.existsByAgendamentoId(agendamento.getId())) {
            return;
        }
        Nps nps = new Nps();
        nps.setAgendamento(agendamento);
        nps.setStatus(StatusNps.PENDENTE);
        nps.setCriadoEm(LocalDateTime.now());
        Nps salvo = repository.save(nps);
        // Notifica o paciente para avaliar o atendimento.
        pushService.notificarNpsPendente(salvo);
    }

    /**
     * Aplica a resposta do paciente (uma nota em estrelas 1..5 por categoria + observação).
     * Regras: bloqueia reavaliação (409), rejeita categoria repetida (400),
     * recalcula a média e marca como RESPONDIDO. Reutilizado pelo admin e pelo app.
     * Deve rodar dentro de uma transação (a coleção de notas é LAZY).
     */
    public Nps responder(Nps nps, ResponderNpsRequest request) {
        // Regra: uma vez respondido, a avaliação não pode ser alterada.
        if (nps.getStatus() == StatusNps.RESPONDIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta avaliação já foi respondida");
        }
        // Não aceita a mesma categoria repetida (violaria a restrição única no banco).
        Set<Long> categoriasVistas = new HashSet<>();
        for (CategoriaNotaRequest item : request.notas()) {
            if (!categoriasVistas.add(item.categoriaId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria de NPS repetida na resposta");
            }
        }
        nps.getNotasCategorias().clear();
        double soma = 0;
        for (CategoriaNotaRequest item : request.notas()) {
            CategoriaNps categoria = categoriaRepository.findById(item.categoriaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Categoria de NPS não encontrada"));
            NpsCategoriaNota nota = new NpsCategoriaNota();
            nota.setNps(nps);
            nota.setCategoria(categoria);
            nota.setNota(item.nota());
            nps.getNotasCategorias().add(nota);
            soma += item.nota();
        }
        nps.setMedia(soma / request.notas().size());
        nps.setObservacao(request.observacao());
        nps.setStatus(StatusNps.RESPONDIDO);
        nps.setRespondidoEm(LocalDateTime.now());
        return repository.save(nps);
    }
}
