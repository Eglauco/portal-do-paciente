package com.example.pop.usuario;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.perfil.Perfil;
import com.example.pop.perfil.PerfilRepository;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

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
    private final UnidadeRepository unidadeRepository;
    private final PerfilRepository perfilRepository;
    private final ExportacaoService exportacaoService;

    public UsuarioController(UsuarioRepository repository, PasswordEncoder passwordEncoder,
            UnidadeRepository unidadeRepository, PerfilRepository perfilRepository,
            ExportacaoService exportacaoService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.unidadeRepository = unidadeRepository;
        this.perfilRepository = perfilRepository;
        this.exportacaoService = exportacaoService;
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

    /**
     * Exporta os usuários que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenados por código.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();
        String filtroEmail = (email == null) ? "" : email.trim();

        List<Usuario> dados = repository.search(codigo, filtroNome, filtroEmail, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Usuario::getId))
                .toList();
        List<ColunaExport<Usuario>> cols = ExportacaoService.filtrar(colunasUsuario(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Usuários", filtrosUsuario(codigo, nome, email), cols, dados)
                : exportacaoService.excel("Usuários", cols, dados);
        String arquivoNome = "usuarios-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + arquivoNome + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasUsuario().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosUsuario(Long codigo, String nome, String email) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", (nome != null && !nome.isBlank()) ? nome.trim() : "Todos"),
                new FiltroAplicado("E-mail", (email != null && !email.isBlank()) ? email.trim() : "Todos"));
    }

    /** Todas as colunas disponíveis do usuário (o usuário escolhe quais exportar). */
    private static List<ColunaExport<Usuario>> colunasUsuario() {
        return List.of(
                ColunaExport.de("Código", u -> u.getId() == null ? "" : String.valueOf(u.getId())),
                ColunaExport.de("Nome", Usuario::getNome),
                ColunaExport.de("E-mail", Usuario::getEmail),
                ColunaExport.de("Unidade", u -> u.getUnidade() == null ? "" : u.getUnidade().getNome()),
                ColunaExport.de("Perfis", u -> u.getPerfis() == null ? "" : u.getPerfis().stream()
                        .map(Perfil::getNome)
                        .sorted()
                        .collect(java.util.stream.Collectors.joining("; "))));
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

        Unidade unidade = resolverUnidade(request.unidadeSaudeId());
        Set<Perfil> perfis = resolverPerfis(request.perfilIds());
        validarUnidadeNosPerfis(unidade, perfis);

        Usuario usuario = new Usuario();
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setUnidade(unidade);
        usuario.setPerfis(perfis);
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
                    Unidade unidade = resolverUnidade(request.unidadeSaudeId());
                    Set<Perfil> perfis = resolverPerfis(request.perfilIds());
                    validarUnidadeNosPerfis(unidade, perfis);
                    existente.setNome(request.nome().trim());
                    existente.setEmail(email);
                    existente.setUnidade(unidade);
                    existente.setPerfis(perfis);
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

    private Unidade resolverUnidade(Long unidadeSaudeId) {
        if (unidadeSaudeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a unidade de saúde");
        }
        return unidadeRepository.findById(unidadeSaudeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unidade de saúde não encontrada"));
    }

    /** Resolve e valida os perfis informados (todos precisam existir). */
    private Set<Perfil> resolverPerfis(List<Long> perfilIds) {
        if (perfilIds == null || perfilIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos um perfil de acesso");
        }
        List<Perfil> encontrados = perfilRepository.findAllById(perfilIds);
        if (encontrados.size() != perfilIds.stream().distinct().count()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Algum perfil informado não existe");
        }
        return new HashSet<>(encontrados);
    }

    /**
     * A unidade ativa do usuário precisa estar entre as unidades dos perfis dele
     * (mesma regra do /auth/unidade). Fecha a inconsistência no cadastro/edição.
     */
    private void validarUnidadeNosPerfis(Unidade unidade, Set<Perfil> perfis) {
        boolean coberta = perfis.stream()
                .flatMap(p -> p.getUnidades().stream())
                .anyMatch(u -> u.getId().equals(unidade.getId()));
        if (!coberta) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A unidade ativa precisa estar entre as unidades dos perfis selecionados");
        }
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
