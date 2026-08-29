package com.example.pop.paciente;

import org.springframework.dao.DataIntegrityViolationException;
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

import com.example.pop.common.Pagina;

@RestController
@RequestMapping("/paciente")
public class PacienteController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final PacienteRepository repository;
    private final PacienteAcessoService acessoService;

    public PacienteController(PacienteRepository repository, PacienteAcessoService acessoService) {
        this.repository = repository;
        this.acessoService = acessoService;
    }

    /**
     * Lista pacientes de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Paciente> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<Paciente> resultado = repository.search(codigo, filtroNome, pageable);

        return new Pagina<>(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Paciente> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Paciente criar(@RequestBody Paciente paciente) {
        paciente.setId(null);
        paciente.setTelefone(PacienteAcessoService.normalizarTelefone(paciente.getTelefone()));
        paciente.setAtivo(false); // a liberação é feita depois, via "gerar código"
        return salvarUnico(paciente);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Paciente> atualizar(@PathVariable Long id, @RequestBody Paciente paciente) {
        return repository.findById(id)
                .map(existente -> {
                    existente.setNome(paciente.getNome());
                    existente.setTelefone(PacienteAcessoService.normalizarTelefone(paciente.getTelefone()));
                    // ativo/código/aparelho são geridos por gerar-codigo/revogar, não pelo corpo.
                    return ResponseEntity.ok(salvarUnico(existente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Libera o paciente (global) e gera um código de ativação para dar a ele (mostrado uma vez). */
    @PostMapping("/{id}/gerar-codigo")
    public ResponseEntity<GerarCodigoResponse> gerarCodigo(@PathVariable Long id) {
        Paciente paciente = repository.findById(id).orElse(null);
        if (paciente == null) {
            return ResponseEntity.notFound().build();
        }
        if (paciente.getTelefone() == null || paciente.getTelefone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe o telefone do paciente antes de gerar o código");
        }
        String codigo = acessoService.gerarCodigo(paciente);
        return ResponseEntity.ok(new GerarCodigoResponse(codigo, paciente.getCodigoAtivacaoExpiraEm()));
    }

    /** Revoga o acesso do paciente ao app (desativa e desloga o aparelho). */
    @PostMapping("/{id}/revogar-acesso")
    public ResponseEntity<Void> revogarAcesso(@PathVariable Long id) {
        Paciente paciente = repository.findById(id).orElse(null);
        if (paciente == null) {
            return ResponseEntity.notFound().build();
        }
        acessoService.revogar(paciente);
        return ResponseEntity.noContent().build();
    }

    private Paciente salvarUnico(Paciente paciente) {
        try {
            return repository.save(paciente);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um paciente com este telefone");
        }
    }
}
