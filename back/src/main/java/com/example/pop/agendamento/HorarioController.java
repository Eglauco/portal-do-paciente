package com.example.pop.agendamento;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.nps.NpsService;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.prontuario.TermoAssinaturaService;
import com.example.pop.push.PushService;
import com.example.pop.storage.StorageService;

import jakarta.validation.Valid;

/**
 * Tela/endpoints "Horário" (back-office): a MARCAÇÃO de um paciente dentro de uma {@link Agenda}. Adiciona paciente,
 * troca o status (com os gatilhos de NPS/TCLE/push, como o antigo agendamento), e expõe histórico e entrega.
 */
@RestController
@RequestMapping("/horario")
public class HorarioController {

    private final HorarioRepository repository;
    private final AgendaRepository agendaRepository;
    private final PacienteRepository pacienteRepository;
    private final HorarioLogService logService;
    private final HorarioEntregaService entregaService;
    private final HorarioEntregaRepository entregaRepository;
    private final NpsService npsService;
    private final PushService pushService;
    private final TermoAssinaturaService termoAssinaturaService;
    private final StorageService storageService;

    public HorarioController(HorarioRepository repository, AgendaRepository agendaRepository,
            PacienteRepository pacienteRepository, HorarioLogService logService, HorarioEntregaService entregaService,
            HorarioEntregaRepository entregaRepository, NpsService npsService, PushService pushService,
            TermoAssinaturaService termoAssinaturaService, StorageService storageService) {
        this.repository = repository;
        this.agendaRepository = agendaRepository;
        this.pacienteRepository = pacienteRepository;
        this.logService = logService;
        this.entregaService = entregaService;
        this.entregaRepository = entregaRepository;
        this.npsService = npsService;
        this.pushService = pushService;
        this.termoAssinaturaService = termoAssinaturaService;
        this.storageService = storageService;
    }

    /** URL de exibição (pré-assinada, 6h) da foto do paciente do horário; null quando não há foto. */
    private String fotoPaciente(Horario h) {
        return storageService.urlFotoPaciente(h.getPaciente().getFotoUrl());
    }

    @GetMapping("/{id}")
    public HorarioResponse buscar(@PathVariable Long id) {
        Horario h = carregar(id);
        return HorarioResponse.from(h, fotoPaciente(h));
    }

    /** Adiciona um paciente (horário) a uma agenda. Nasce aguardando confirmação + notifica o paciente. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HorarioResponse criar(@Valid @RequestBody HorarioRequest request, @AuthenticationPrincipal Jwt jwt) {
        Horario h = new Horario();
        aplicar(h, request);
        h.setStatusAgendamento(StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE);
        Horario salvo = repository.save(h);
        logService.registrarDaUnidade(salvo, null, StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE, uidDoToken(jwt));
        entregaService.notificarNovoAgendamento(salvo);
        return HorarioResponse.from(salvo, fotoPaciente(salvo));
    }

    /** Edita o horário (paciente/hora) e/ou troca o status, disparando NPS/TCLE/push como o fluxo antigo. */
    @PutMapping("/{id}")
    public HorarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody HorarioRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Horario h = carregar(id);
        StatusAgendamento anterior = h.getStatusAgendamento();
        aplicar(h, request);
        if (request.statusAgendamento() != null) {
            h.setStatusAgendamento(request.statusAgendamento());
        }
        Horario salvo = repository.save(h);
        logService.registrarDaUnidade(salvo, anterior, salvo.getStatusAgendamento(), uidDoToken(jwt));
        // Na presença: gera o NPS e dispara os termos (TCLE) do procedimento, best-effort.
        npsService.gerarSeNecessario(salvo);
        try {
            termoAssinaturaService.dispararSeNecessario(salvo);
        } catch (RuntimeException ignored) {
            // não impede a atualização do horário
        }
        // Ao MARCAR falta (transição), notifica o paciente para justificar.
        if (salvo.getStatusAgendamento() == StatusAgendamento.FALTA_PACIENTE
                && anterior != StatusAgendamento.FALTA_PACIENTE) {
            pushService.notificarFaltaPaciente(salvo);
        }
        return HorarioResponse.from(salvo, fotoPaciente(salvo));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Horário não encontrado.");
        }
        repository.deleteById(id);
    }

    /** Destinatários da notificação deste horário e o estado de entrega de cada um. */
    @GetMapping("/{id}/entrega")
    public ResponseEntity<List<HorarioEntregaResponse>> entrega(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(entregaRepository.findByHorario_IdOrderByIdAsc(id).stream()
                .map(HorarioEntregaResponse::from).toList());
    }

    /** Linha do tempo das trocas de status (auditoria). */
    @GetMapping("/{id}/logs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<HorarioLogResponse>> logs(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logService.listar(id));
    }

    private Horario carregar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Horário não encontrado."));
    }

    private void aplicar(Horario h, HorarioRequest request) {
        Agenda agenda = agendaRepository.findById(request.agendaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agenda não encontrada"));
        h.setAgenda(agenda);
        h.setDataHora(agenda.getData().atTime(request.horaInicio()));
        h.setHoraFim(request.horaFim());
        h.setPaciente(pacienteRepository.findById(request.pacienteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paciente não encontrado")));
    }

    private static Long uidDoToken(Jwt jwt) {
        return jwt != null && jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
    }
}
