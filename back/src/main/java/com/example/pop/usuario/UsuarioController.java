package com.example.pop.usuario;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuario")
public class UsuarioController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;
    /** Tamanho mínimo da senha. */
    private static final int SENHA_MIN = 6;

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Lista usuários de forma paginada, com filtros opcionais por código, nome e e-mail.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Usuario> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();
        String filtroEmail = (email == null) ? "" : email.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<Usuario> resultado = repository.search(codigo, filtroNome, filtroEmail, pageable);

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
    public ResponseEntity<Usuario> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Usuario criar(@Valid @RequestBody UsuarioRequest request) {
        String email = request.email().trim();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail");
        }
        String senha = validarSenhaObrigatoria(request.senha());

        Usuario usuario = new Usuario();
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        return salvarUnico(usuario);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request) {
        return repository.findById(id)
                .map(existente -> {
                    String email = request.email().trim();
                    boolean emailDeOutro = repository.findByEmailIgnoreCase(email)
                            .map(outro -> !outro.getId().equals(id))
                            .orElse(false);
                    if (emailDeOutro) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail");
                    }
                    existente.setNome(request.nome().trim());
                    existente.setEmail(email);
                    // Senha em branco na edição = mantém a atual. Não normalizamos a senha
                    // (é comparada crua no login), só validamos o tamanho.
                    String senha = request.senha();
                    if (senha != null && !senha.isBlank()) {
                        existente.setSenhaHash(passwordEncoder.encode(validarTamanhoSenha(senha)));
                    }
                    return ResponseEntity.ok(salvarUnico(existente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Salva tratando a violação do índice único de e-mail como 409 (fecha a corrida TOCTOU). */
    private Usuario salvarUnico(Usuario usuario) {
        try {
            return repository.save(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail");
        }
    }

    private String validarSenhaObrigatoria(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha");
        }
        return validarTamanhoSenha(senha);
    }

    private String validarTamanhoSenha(String senha) {
        if (senha.length() < SENHA_MIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve ter ao menos " + SENHA_MIN + " caracteres");
        }
        return senha;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
