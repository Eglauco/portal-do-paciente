package com.example.pop.prontuario;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.common.Pagina;
import com.example.pop.push.PushService;
import com.example.pop.storage.StorageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/prontuario")
public class ProntuarioController {

    private static final int TAMANHO_MAXIMO = 100;

    private final ProntuarioRepository repository;
    private final AgendamentoRepository agendamentoRepository;
    private final StorageService storageService;
    private final PushService pushService;

    public ProntuarioController(ProntuarioRepository repository, AgendamentoRepository agendamentoRepository,
            StorageService storageService, PushService pushService) {
        this.repository = repository;
        this.agendamentoRepository = agendamentoRepository;
        this.storageService = storageService;
        this.pushService = pushService;
    }

    @GetMapping
    public Pagina<ProntuarioResponse> listar(
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "id"));
        Page<Prontuario> resultado = repository.search(numero == null ? "" : numero, pacienteId, pageable);
        List<ProntuarioResponse> content = resultado.getContent().stream().map(ProntuarioResponse::from).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProntuarioDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(p -> ResponseEntity.ok(ProntuarioDetalheResponse.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProntuarioDetalheResponse criar(@Valid @RequestBody ProntuarioRequest request) {
        if (repository.existsByNumeroAtendimento(request.numeroAtendimento().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Número do atendimento já cadastrado");
        }
        Prontuario prontuario = new Prontuario();
        aplicar(prontuario, request);
        ProntuarioDetalheResponse resposta = ProntuarioDetalheResponse.from(repository.save(prontuario));
        // Notifica o paciente sobre o novo prontuário.
        pushService.notificarProntuario(true);
        return resposta;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProntuarioDetalheResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody ProntuarioRequest request) {
        return repository.findById(id)
                .map(prontuario -> {
                    if (repository.existsByNumeroAtendimentoAndIdNot(request.numeroAtendimento().trim(), id)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Número do atendimento já cadastrado");
                    }
                    int documentosAntes = prontuario.getDocumentos().size();
                    aplicar(prontuario, request);
                    ProntuarioDetalheResponse resposta = ProntuarioDetalheResponse.from(repository.save(prontuario));
                    // Notifica o paciente se novos documentos foram adicionados.
                    if (request.documentos().size() > documentosAntes) {
                        pushService.notificarProntuario(false);
                    }
                    return ResponseEntity.ok(resposta);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        Prontuario prontuario = repository.findById(id).orElse(null);
        if (prontuario == null) {
            return ResponseEntity.notFound().build();
        }
        // Remove os arquivos do S3 para não deixar lixo.
        prontuario.getDocumentos().forEach(d -> {
            try {
                storageService.excluirPorUrl(d.getUrl());
            } catch (RuntimeException ignored) {
                // não impede a exclusão do prontuário se a limpeza falhar
            }
        });
        repository.delete(prontuario);
        return ResponseEntity.noContent().build();
    }

    private void aplicar(Prontuario prontuario, ProntuarioRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(request.agendamentoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agendamento não encontrado"));
        prontuario.setAgendamento(agendamento);
        prontuario.setNumeroAtendimento(request.numeroAtendimento().trim());

        List<Documento> documentos = request.documentos().stream().map(dr -> {
            Documento d = new Documento();
            d.setNome(dr.nome().trim());
            d.setUrl(dr.url());
            return d;
        }).toList();
        prontuario.substituirDocumentos(documentos);
    }
}
