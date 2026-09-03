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

import com.example.pop.procedimento.Procedimento;
import com.example.pop.procedimento.ProcedimentoRepository;

import jakarta.validation.Valid;

/** CRUD dos lembretes de um procedimento (back-office). Sob /procedimento/** → ADMIN. */
@RestController
@RequestMapping("/procedimento")
public class LembreteController {

    private final LembreteRepository repository;
    private final ProcedimentoRepository procedimentoRepository;

    public LembreteController(LembreteRepository repository, ProcedimentoRepository procedimentoRepository) {
        this.repository = repository;
        this.procedimentoRepository = procedimentoRepository;
    }

    /** Lembretes de um procedimento (maior antecedência primeiro). */
    @GetMapping("/{procedimentoId}/lembretes")
    public List<LembreteResponse> listar(@PathVariable Long procedimentoId) {
        return repository.findByProcedimentoIdOrderByHorasAntecedenciaDesc(procedimentoId)
                .stream().map(LembreteResponse::from).toList();
    }

    @PostMapping("/{procedimentoId}/lembretes")
    @ResponseStatus(HttpStatus.CREATED)
    public LembreteResponse criar(@PathVariable Long procedimentoId, @Valid @RequestBody LembreteRequest request) {
        Procedimento procedimento = procedimentoRepository.findById(procedimentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado"));
        Lembrete l = new Lembrete();
        l.setProcedimento(procedimento);
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
