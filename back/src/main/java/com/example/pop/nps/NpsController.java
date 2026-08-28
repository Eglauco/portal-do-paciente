package com.example.pop.nps;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.categorianps.CategoriaNps;
import com.example.pop.categorianps.CategoriaNpsRepository;
import com.example.pop.common.Pagina;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/nps")
public class NpsController {

    private static final int TAMANHO_MAXIMO = 100;

    private final NpsRepository repository;
    private final CategoriaNpsRepository categoriaRepository;

    public NpsController(NpsRepository repository, CategoriaNpsRepository categoriaRepository) {
        this.repository = repository;
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping
    public Pagina<NpsResponse> listar(
            @RequestParam(required = false) StatusNps status,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm"));
        Page<Nps> resultado = repository.search(status, pacienteId, unidadeId, pageable);
        List<NpsResponse> content = resultado.getContent().stream().map(NpsResponse::from).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<NpsDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(nps -> ResponseEntity.ok(NpsDetalheResponse.from(nps)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Registra a resposta do paciente (uma nota 0 a 10 por categoria + observação opcional). */
    @PostMapping("/{id}/responder")
    @Transactional
    public ResponseEntity<NpsDetalheResponse> responder(@PathVariable Long id,
            @Valid @RequestBody ResponderNpsRequest request) {
        Nps nps = obter(id);
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
        return ResponseEntity.ok(NpsDetalheResponse.from(repository.save(nps)));
    }

    @PostMapping("/{id}/expirar")
    @Transactional
    public ResponseEntity<NpsDetalheResponse> expirar(@PathVariable Long id) {
        Nps nps = obter(id);
        nps.setStatus(StatusNps.EXPIRADO);
        return ResponseEntity.ok(NpsDetalheResponse.from(repository.save(nps)));
    }

    private Nps obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NPS não encontrado"));
    }
}
