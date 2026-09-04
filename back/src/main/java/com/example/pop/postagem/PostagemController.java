package com.example.pop.postagem;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.common.Ref;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.push.PushService;
import com.example.pop.storage.StorageService;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/postagem")
public class PostagemController {

    private static final int TAMANHO_MAXIMO = 100;
    /** Validade das URLs de imagem no feed/edição. */
    static final Duration VALIDADE_IMAGEM = Duration.ofDays(7);

    private final PostagemRepository repository;
    private final CurtidaRepository curtidaRepository;
    private final ComentarioRepository comentarioRepository;
    private final UnidadeRepository unidadeRepository;
    private final StorageService storageService;
    private final PushService pushService;
    private final ExportacaoService exportacaoService;

    public PostagemController(PostagemRepository repository, CurtidaRepository curtidaRepository,
            ComentarioRepository comentarioRepository, UnidadeRepository unidadeRepository,
            StorageService storageService, PushService pushService, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.curtidaRepository = curtidaRepository;
        this.comentarioRepository = comentarioRepository;
        this.unidadeRepository = unidadeRepository;
        this.storageService = storageService;
        this.pushService = pushService;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    public Pagina<PostagemResponse> listar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Boolean comentarios,
            @RequestParam(required = false) Boolean novoComentario,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm"));
        Page<Postagem> resultado = repository.search(titulo == null ? "" : titulo, unidadeId, comentarios,
                novoComentario, pageable);
        List<PostagemResponse> content = resultado.getContent().stream().map(this::toResponse).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Exporta as postagens que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Mais recentes primeiro.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Boolean comentarios,
            @RequestParam(required = false) Boolean novoComentario) {
        List<Postagem> dados = repository
                .search(titulo == null ? "" : titulo, unidadeId, comentarios, novoComentario, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Postagem::getCriadoEm).reversed())
                .toList();
        List<ColunaExport<Postagem>> colunas = colunasPostagem();

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Rede Social", filtrosPostagem(titulo, unidadeId, comentarios, novoComentario),
                        colunas, dados)
                : exportacaoService.excel("Rede Social", colunas, dados);
        String nome = "postagens-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosPostagem(String titulo, Long unidadeId, Boolean comentarios,
            Boolean novoComentario) {
        String unidade = unidadeId == null ? "Todas"
                : unidadeRepository.findById(unidadeId).map(Unidade::getNome).orElse("#" + unidadeId);
        String comentariosLabel = comentarios == null ? "Todos" : (comentarios ? "Habilitados" : "Desabilitados");
        String novoLabel = novoComentario == null ? "Todos" : (novoComentario ? "Com novos" : "Sem novos");
        return List.of(
                new FiltroAplicado("Título", titulo == null || titulo.isBlank() ? "Todos" : titulo),
                new FiltroAplicado("Comentários", comentariosLabel),
                new FiltroAplicado("Comentários novos", novoLabel),
                new FiltroAplicado("Unidade", unidade));
    }

    /** Colunas da exportação — todas as informações úteis da tela, incluindo as contagens. */
    private List<ColunaExport<Postagem>> colunasPostagem() {
        return List.of(
                ColunaExport.de("Título", Postagem::getTitulo),
                ColunaExport.de("Descrição", Postagem::getDescricao),
                ColunaExport.de("Unidade", p -> p.getUnidadeSaude().getNome()),
                ColunaExport.de("Curtidas", p -> String.valueOf(curtidaRepository.countByPostagemId(p.getId()))),
                ColunaExport.de("Comentários", p -> String.valueOf(comentarioRepository.countByPostagemId(p.getId()))),
                ColunaExport.de("Comentário novo", p -> p.temComentarioNovo() ? "Sim" : "Não"),
                ColunaExport.de("Total de curtidas visível", p -> p.isMostrarTotalCurtidas() ? "Sim" : "Não"),
                ColunaExport.de("Comentários habilitados", p -> p.isHabilitarComentarios() ? "Sim" : "Não"),
                ColunaExport.de("Publicado em", p -> p.getCriadoEm() == null ? "" : p.getCriadoEm().format(DATA_HORA)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostagemDetalheResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(p -> {
                    // Abrir a postagem (o admin vê os comentários aqui) zera o status "comentário novo".
                    // Update pontual com o "high-water mark" (o último comentário que ele viu): não
                    // sobrescreve ultimo_comentario_paciente_em se chegar comentário novo em paralelo.
                    if (p.temComentarioNovo()) {
                        repository.marcarComentariosVistos(p.getId(), p.getUltimoComentarioPacienteEm());
                    }
                    return ResponseEntity.ok(toDetalhe(p));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostagemDetalheResponse criar(@Valid @RequestBody PostagemRequest request) {
        Postagem postagem = new Postagem();
        aplicar(postagem, request);
        postagem.setCriadoEm(LocalDateTime.now());
        Postagem salva = repository.save(postagem);
        // Notifica os pacientes (push) sobre a nova publicação no feed.
        pushService.notificarNovaPostagem(salva);
        return toDetalhe(salva);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostagemDetalheResponse> atualizar(@PathVariable Long id,
            @Valid @RequestBody PostagemRequest request) {
        return repository.findById(id)
                .map(postagem -> {
                    String urlAntiga = postagem.getUrl();
                    aplicar(postagem, request);
                    PostagemDetalheResponse resposta = toDetalhe(repository.save(postagem));
                    // Se a imagem foi trocada, remove a antiga do S3.
                    if (urlAntiga != null && !urlAntiga.equals(postagem.getUrl())) {
                        try {
                            storageService.excluirPorUrl(urlAntiga);
                        } catch (RuntimeException ignored) {
                            // não impede a atualização
                        }
                    }
                    return ResponseEntity.ok(resposta);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        Postagem postagem = repository.findById(id).orElse(null);
        if (postagem == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            storageService.excluirPorUrl(postagem.getUrl());
        } catch (RuntimeException ignored) {
            // não impede a exclusão da postagem
        }
        repository.delete(postagem); // curtidas/comentários caem por ON DELETE CASCADE
        return ResponseEntity.noContent().build();
    }

    /** Exclui um comentário (moderação pelo admin). Respostas caem por ON DELETE CASCADE. */
    @DeleteMapping("/comentario/{comentarioId}")
    public ResponseEntity<Void> excluirComentario(@PathVariable Long comentarioId) {
        if (!comentarioRepository.existsById(comentarioId)) {
            return ResponseEntity.notFound().build();
        }
        comentarioRepository.deleteById(comentarioId);
        return ResponseEntity.noContent().build();
    }

    /** Responde a um comentário (administração respondendo dúvidas dos pacientes). */
    @PostMapping("/comentario/{comentarioId}/responder")
    @Transactional
    public ResponseEntity<ComentarioResponse> responderComentario(@PathVariable Long comentarioId,
            @Valid @RequestBody ComentarRequest request, @AuthenticationPrincipal Jwt jwt) {
        Comentario pai = comentarioRepository.findById(comentarioId).orElse(null);
        if (pai == null) {
            return ResponseEntity.notFound().build();
        }
        Long adminId = uidDoToken(jwt);
        // Threading de 1 nível: a resposta se ancora sempre no comentário-raiz.
        Comentario raiz = pai.getComentarioPai() != null ? pai.getComentarioPai() : pai;
        Comentario resposta = new Comentario();
        resposta.setPostagem(pai.getPostagem());
        resposta.setComentarioPai(raiz);
        resposta.setAutor(request.autor().trim());
        resposta.setUsuarioId(adminId); // dono admin — pode editar por 15 min; sem dono paciente
        resposta.setTexto(request.texto().trim());
        resposta.setCriadoEm(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ComentarioResponse.from(comentarioRepository.save(resposta), null, adminId));
    }

    /** Edita o próprio comentário do admin — permitido só até 15 min após criar. */
    @PutMapping("/comentario/{comentarioId}")
    @Transactional
    public ComentarioResponse editarComentario(@PathVariable Long comentarioId,
            @Valid @RequestBody EditarComentarioRequest request, @AuthenticationPrincipal Jwt jwt) {
        Long adminId = uidDoToken(jwt);
        Comentario c = comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentário não encontrado"));
        if (adminId == null || !adminId.equals(c.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só o autor pode editar este comentário.");
        }
        if (c.getCriadoEm().isBefore(LocalDateTime.now().minusMinutes(ComentarioResponse.JANELA_EDICAO_MINUTOS))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O prazo para editar este comentário (" + ComentarioResponse.JANELA_EDICAO_MINUTOS + " min) expirou.");
        }
        c.setTexto(request.texto().trim());
        c.setEditadoEm(LocalDateTime.now());
        return ComentarioResponse.from(comentarioRepository.save(c), null, adminId);
    }

    /** Id do usuário admin a partir do claim "uid" do token; nulo se ausente. */
    private Long uidDoToken(Jwt jwt) {
        Object uid = jwt == null ? null : jwt.getClaim("uid");
        return uid instanceof Number numero ? numero.longValue() : null;
    }

    // ---------- helpers ----------

    private void aplicar(Postagem postagem, PostagemRequest request) {
        Unidade unidade = unidadeRepository.findById(request.unidadeSaudeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unidade não encontrada"));
        postagem.setTitulo(request.titulo().trim());
        postagem.setDescricao(request.descricao());
        postagem.setMostrarTotalCurtidas(request.mostrarTotalCurtidas());
        postagem.setHabilitarComentarios(request.habilitarComentarios());
        postagem.setUnidadeSaude(unidade);
        // Guarda a URL do objeto sem query (o front pode reenviar uma URL assinada na edição).
        postagem.setUrl(request.url().split("\\?")[0]);
    }

    private PostagemResponse toResponse(Postagem p) {
        return new PostagemResponse(
                p.getId(),
                p.getTitulo(),
                p.getDescricao(),
                new Ref(p.getUnidadeSaude().getId(), p.getUnidadeSaude().getNome()),
                p.isMostrarTotalCurtidas(),
                p.isHabilitarComentarios(),
                storageService.urlVisualizacao(p.getUrl(), VALIDADE_IMAGEM),
                p.getCriadoEm(),
                curtidaRepository.countByPostagemId(p.getId()),
                comentarioRepository.countByPostagemId(p.getId()),
                p.temComentarioNovo());
    }

    private PostagemDetalheResponse toDetalhe(Postagem p) {
        return new PostagemDetalheResponse(
                p.getId(),
                p.getTitulo(),
                p.getDescricao(),
                p.isMostrarTotalCurtidas(),
                p.isHabilitarComentarios(),
                new Ref(p.getUnidadeSaude().getId(), p.getUnidadeSaude().getNome()),
                storageService.urlVisualizacao(p.getUrl(), VALIDADE_IMAGEM),
                p.getCriadoEm(),
                curtidaRepository.countByPostagemId(p.getId()),
                comentarioRepository.countByPostagemId(p.getId()));
    }
}
