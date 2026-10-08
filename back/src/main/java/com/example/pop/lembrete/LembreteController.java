package com.example.pop.lembrete;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.example.pop.configuracaoagenda.ConfiguracaoAgenda;
import com.example.pop.configuracaoagenda.ConfiguracaoAgendaRepository;

import jakarta.validation.Valid;

/** CRUD dos lembretes de um configuracaoAgenda (back-office). Sob /configuracaoAgenda/** → ADMIN. */
@RestController
@RequestMapping("/configuracao-agenda")
public class LembreteController {

    private final LembreteRepository repository;
    private final ConfiguracaoAgendaRepository configuracaoAgendaRepository;

    public LembreteController(LembreteRepository repository, ConfiguracaoAgendaRepository configuracaoAgendaRepository) {
        this.repository = repository;
        this.configuracaoAgendaRepository = configuracaoAgendaRepository;
    }

    /** Lembretes de um configuracaoAgenda (maior antecedência primeiro). */
    @GetMapping("/{configuracaoAgendaId}/lembretes")
    public List<LembreteResponse> listar(@PathVariable Long configuracaoAgendaId) {
        return repository.findByConfiguracaoAgendaIdOrderByHorasAntecedenciaDesc(configuracaoAgendaId)
                .stream().map(LembreteResponse::from).toList();
    }

    @PostMapping("/{configuracaoAgendaId}/lembretes")
    @ResponseStatus(HttpStatus.CREATED)
    public LembreteResponse criar(@PathVariable Long configuracaoAgendaId, @Valid @RequestBody LembreteRequest request) {
        ConfiguracaoAgenda configuracaoAgenda = configuracaoAgendaRepository.findById(configuracaoAgendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Configuração da Agenda não encontrada"));
        Lembrete l = new Lembrete();
        l.setConfiguracaoAgenda(configuracaoAgenda);
        l.setTexto(request.texto().trim());
        l.setHorasAntecedencia(request.horasAntecedencia());
        l.setCriadoEm(LocalDateTime.now());
        return LembreteResponse.from(repository.save(l));
    }

    @PutMapping("/lembretes/{id}")
    public ResponseEntity<LembreteResponse> atualizar(@PathVariable Long id, @Valid @RequestBody LembreteRequest request) {
        return repository.findById(id)
                .map(l -> {
                    l.setTexto(request.texto().trim());
                    l.setHorasAntecedencia(request.horasAntecedencia());
                    return ResponseEntity.ok(LembreteResponse.from(repository.save(l)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/lembretes/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id); // disparos caem por ON DELETE CASCADE
        return ResponseEntity.noContent().build();
    }
}
