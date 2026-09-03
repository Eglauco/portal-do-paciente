package com.example.pop.nps;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.StatusAgendamento;
import com.example.pop.categorianps.CategoriaNps;
import com.example.pop.categorianps.CategoriaNpsRepository;
import com.example.pop.push.PushService;

@Service
public class NpsService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final NpsRepository repository;
    private final CategoriaNpsRepository categoriaRepository;
    private final PushService pushService;

    /** Proxy do próprio bean, para chamar dispararUm() com transação por item (evita self-invocation). */
    @Autowired
    @Lazy
    private NpsService self;

    public NpsService(NpsRepository repository, CategoriaNpsRepository categoriaRepository, PushService pushService) {
        this.repository = repository;
        this.categoriaRepository = categoriaRepository;
        this.pushService = pushService;
    }

    /**
     * Gera um NPS vinculado ao agendamento quando o status for PRESENCA_PACIENTE e
     * ainda não existir um NPS. O disparo é AGENDADO para {@code horasNps} horas após
     * a presença (0 = na hora): com 0 dispara já; senão fica agendado (invisível ao
     * paciente) e o job {@link #dispararAgendados()} envia quando chegar a hora.
     */
    public void gerarSeNecessario(Agendamento agendamento) {
        if (agendamento.getStatusAgendamento() != StatusAgendamento.PRESENCA_PACIENTE) {
            return;
        }
        if (repository.existsByAgendamentoId(agendamento.getId())) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now(FUSO);
        int horas = agendamento.getProcedimento().getHorasNps();
        LocalDateTime dispararEm = agora.plusHours(horas);

        Nps nps = new Nps();
        nps.setAgendamento(agendamento);
        nps.setStatus(StatusNps.PENDENTE);
        nps.setCriadoEm(agora);
        nps.setDispararEm(dispararEm);

        if (!dispararEm.isAfter(agora)) {
            // 0 horas: dispara na hora (comportamento atual).
            nps.setDisparadoEm(agora);
            Nps salvo = repository.save(nps);
            pushService.notificarNpsPendente(salvo);
        } else {
            // Agendado: fica invisível ao paciente até o job disparar.
            repository.save(nps);
        }
    }

    /**
     * Orquestra o disparo dos NPS vencidos (job a cada 5 min). Cada NPS é tratado em
     * TRANSAÇÃO PRÓPRIA (dispararUm) e o push só é enviado DEPOIS do commit — assim uma
     * falha/timeout de um item não desfaz os já disparados nem reenvia push duplicado.
     */
    public int dispararAgendados() {
        int enviados = 0;
        for (Long id : repository.idsParaDisparar(LocalDateTime.now(FUSO))) {
            Nps disparado = self.dispararUm(id);
            if (disparado != null) {
                // Fora da transação (já comitada): um push travado não segura o BD.
                pushService.notificarNpsPendente(disparado);
                enviados++;
            }
        }
        return enviados;
    }

    /**
     * Dispara UM NPS em transação própria: só se ainda PENDENTE não disparado e o
     * agendamento ainda em PRESENCA_PACIENTE (senão cancela — remove; nunca foi visto).
     * Retorna o NPS a notificar (grafo EAGER, seguro após o commit) ou null.
     */
    @Transactional
    public Nps dispararUm(Long npsId) {
        Nps nps = repository.findById(npsId).orElse(null);
        if (nps == null || nps.getDisparadoEm() != null) {
            return null;
        }
        if (nps.getAgendamento().getStatusAgendamento() != StatusAgendamento.PRESENCA_PACIENTE) {
            repository.delete(nps);
            return null;
        }
        nps.setDisparadoEm(LocalDateTime.now(FUSO));
        return repository.save(nps);
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
