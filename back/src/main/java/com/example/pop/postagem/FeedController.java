package com.example.pop.postagem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.common.Ref;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.storage.StorageService;

import jakarta.validation.Valid;

/** Endpoints da rede social usados pelo app (feed, curtir, comentar). */
@RestController
public class FeedController {

    private static final int TAMANHO_MAXIMO = 100;
    /** Janela em que o autor ainda pode editar o próprio comentário (fonte única no response). */
    private static final int JANELA_EDICAO_MIN = ComentarioResponse.JANELA_EDICAO_MINUTOS;

    private final PostagemRepository repository;
    private final CurtidaRepository curtidaRepository;
    private final ComentarioRepository comentarioRepository;
    private final StorageService storageService;
    private final PacienteAcessoService acessoService;

    public FeedController(PostagemRepository repository, CurtidaRepository curtidaRepository,
            ComentarioRepository comentarioRepository, StorageService storageService,
            PacienteAcessoService acessoService) {
        this.repository = repository;
        this.curtidaRepository = curtidaRepository;
        this.comentarioRepository = comentarioRepository;
        this.storageService = storageService;
        this.acessoService = acessoService;
    }

    @GetMapping("/feed")
    public Pagina<FeedResponse> feed(
            @RequestParam(required = false) String dispositivoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String disp = dispositivoId == null ? "" : dispositivoId.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm"));
        Page<Postagem> resultado = repository.findAll(pageable);
        List<FeedResponse> content = resultado.getContent().stream().map(p -> toFeed(p, disp)).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Detalhe de uma postagem no formato do feed (para a tela de detalhe do app). */
    @GetMapping("/feed/{id}")
    public FeedResponse postagem(@PathVariable Long id, @RequestParam(required = false) String dispositivoId) {
        return toFeed(obter(id), dispositivoId == null ? "" : dispositivoId.trim());
    }

    /** Curte ou descurte (toggle) a postagem para um aparelho. */
    @PostMapping("/postagem/{id}/curtir")
    @Transactional
    public CurtirResponse curtir(@PathVariable Long id, @Valid @RequestBody CurtirRequest request) {
        Postagem postagem = obter(id);
        String disp = request.dispositivoId().trim();

        Optional<Curtida> existente = curtidaRepository.findByPostagemIdAndDispositivoId(id, disp);
        boolean curtido;
        if (existente.isPresent()) {
            curtidaRepository.delete(existente.get());
            curtido = false;
        } else {
            Curtida curtida = new Curtida();
            curtida.setPostagem(postagem);
            curtida.setDispositivoId(disp);
            curtida.setCriadoEm(LocalDateTime.now());
            curtidaRepository.save(curtida);
            curtido = true;
        }
        return new CurtirResponse(curtido, curtidaRepository.countByPostagemId(id));
    }

    @GetMapping("/postagem/{id}/comentarios")
    public Pagina<ComentarioResponse> comentarios(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        // Leitura é pública; com token, marca-se "meu" nos comentários de quem lê —
        // paciente (app) OU admin (front) — para mostrar editar/excluir. Só o claim do
        // id, sem validar sessão: é uma dica de UI (a escrita valida de verdade).
        Long pacienteAtual = pacienteIdDoToken(jwt);
        Long adminAtual = usuarioIdDoToken(jwt);
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        Pageable pageable = PageRequest.of(pagina, tamanho);
        Page<Comentario> resultado = comentarioRepository
                .findByPostagemIdAndComentarioPaiIsNullOrderByCriadoEmDesc(id, pageable);

        // Carrega as respostas dos comentários-raiz desta página em uma única consulta.
        List<Long> raizes = resultado.getContent().stream().map(Comentario::getId).toList();
        Map<Long, List<Comentario>> porPai = raizes.isEmpty()
                ? Map.of()
                : comentarioRepository.findByComentarioPaiIdInOrderByCriadoEmAsc(raizes).stream()
                        .collect(Collectors.groupingBy(r -> r.getComentarioPai().getId()));

        List<ComentarioResponse> content = resultado.getContent().stream()
                .map(c -> ComentarioResponse.from(c, porPai.getOrDefault(c.getId(), List.of()), pacienteAtual, adminAtual))
                .toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @PostMapping("/postagem/{id}/comentarios")
    public ComentarioResponse comentar(@PathVariable Long id, @Valid @RequestBody ComentarRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Postagem postagem = obter(id);
        if (!postagem.isHabilitarComentarios()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comentários desativados para esta postagem");
        }
        // Revalida a sessão (ativo + aparelho vinculado) e usa o nome do paciente
        // validado — autor confiável, nunca vindo do corpo.
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        Comentario comentario = new Comentario();
        comentario.setPostagem(postagem);
        comentario.setAutor(nomeExibicao(paciente.getNome()));
        comentario.setPacienteId(paciente.getId());
        comentario.setTexto(request.texto().trim());
        comentario.setCriadoEm(LocalDateTime.now());
        Comentario salvo = comentarioRepository.save(comentario);
        marcarComentarioNovo(postagem);
        return ComentarioResponse.from(salvo, paciente.getId(), null);
    }

    /** Responde a um comentário (outro paciente pode ajudar a tirar a dúvida). */
    @PostMapping("/postagem/{id}/comentarios/{comentarioId}/responder")
    @Transactional
    public ComentarioResponse responder(@PathVariable Long id, @PathVariable Long comentarioId,
            @Valid @RequestBody ComentarRequest request, @AuthenticationPrincipal Jwt jwt) {
        Postagem postagem = obter(id);
        if (!postagem.isHabilitarComentarios()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comentários desativados para esta postagem");
        }
        Comentario pai = comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentário não encontrado"));
        if (!pai.getPostagem().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comentário não pertence à postagem");
        }
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        // Threading de 1 nível: a resposta se ancora sempre no comentário-raiz.
        Comentario raiz = pai.getComentarioPai() != null ? pai.getComentarioPai() : pai;
        Comentario resposta = new Comentario();
        resposta.setPostagem(postagem);
        resposta.setComentarioPai(raiz);
        resposta.setAutor(nomeExibicao(paciente.getNome()));
        resposta.setPacienteId(paciente.getId());
        resposta.setTexto(request.texto().trim());
        resposta.setCriadoEm(LocalDateTime.now());
        Comentario salva = comentarioRepository.save(resposta);
        marcarComentarioNovo(postagem);
        return ComentarioResponse.from(salva, paciente.getId(), null);
    }

    /** Edita o próprio comentário — permitido só até {@value #JANELA_EDICAO_MIN} min após criar. */
    @PutMapping("/postagem/{id}/comentarios/{comentarioId}")
    @Transactional
    public ComentarioResponse editar(@PathVariable Long id, @PathVariable Long comentarioId,
            @Valid @RequestBody EditarComentarioRequest request, @AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        Comentario c = comentarioDaPostagem(id, comentarioId);
        exigirDono(c, paciente);
        if (c.getCriadoEm().isBefore(LocalDateTime.now().minusMinutes(JANELA_EDICAO_MIN))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O prazo para editar este comentário (" + JANELA_EDICAO_MIN + " min) expirou.");
        }
        c.setTexto(request.texto().trim());
        c.setEditadoEm(LocalDateTime.now());
        return ComentarioResponse.from(comentarioRepository.save(c), paciente.getId(), null);
    }

    /**
     * Exclui o próprio comentário (sem prazo). Se for um comentário-raiz, apaga
     * também todas as respostas abaixo — inclusive de outros pacientes.
     */
    @DeleteMapping("/postagem/{id}/comentarios/{comentarioId}")
    @Transactional
    public ResponseEntity<Void> excluir(@PathVariable Long id, @PathVariable Long comentarioId,
            @AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        Comentario c = comentarioDaPostagem(id, comentarioId);
        exigirDono(c, paciente);
        if (c.getComentarioPai() == null) {
            List<Comentario> respostas = comentarioRepository.findByComentarioPaiIdOrderByCriadoEmAsc(c.getId());
            if (!respostas.isEmpty()) {
                comentarioRepository.deleteAll(respostas);
            }
        }
        comentarioRepository.delete(c);
        return ResponseEntity.noContent().build();
    }

    // ---------- helpers ----------

    /** Marca a postagem com "comentário novo" (um paciente comentou) para o admin ver na lista. */
    private void marcarComentarioNovo(Postagem postagem) {
        postagem.setUltimoComentarioPacienteEm(LocalDateTime.now());
        repository.save(postagem);
    }

    /** Id do paciente a partir do claim do token (sem validar sessão); nulo se não autenticado. */
    private Long pacienteIdDoToken(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object pid = jwt.getClaim("pid");
        return pid instanceof Number numero ? numero.longValue() : null;
    }

    /** Id do usuário admin a partir do claim do token (sem validar sessão); nulo se não for admin. */
    private Long usuarioIdDoToken(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object uid = jwt.getClaim("uid");
        return uid instanceof Number numero ? numero.longValue() : null;
    }

    /** Carrega o comentário garantindo que pertence à postagem informada. */
    private Comentario comentarioDaPostagem(Long postagemId, Long comentarioId) {
        Comentario c = comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentário não encontrado"));
        if (!c.getPostagem().getId().equals(postagemId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comentário não pertence à postagem");
        }
        return c;
    }

    /** Garante que o paciente logado é o dono do comentário (403 caso contrário). */
    private void exigirDono(Comentario c, Paciente paciente) {
        if (c.getPacienteId() == null || !c.getPacienteId().equals(paciente.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só o autor pode alterar este comentário.");
        }
    }

    /**
     * Nome exibido no comentário: primeiro nome + inicial do sobrenome
     * (ex.: "Mariana D."), por privacidade no feed público.
     */
    private String nomeExibicao(String nome) {
        if (nome == null || nome.isBlank()) {
            return "Paciente";
        }
        String[] partes = nome.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        String sobrenome = partes[partes.length - 1];
        return partes[0] + " " + Character.toUpperCase(sobrenome.charAt(0)) + ".";
    }

    private Postagem obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postagem não encontrada"));
    }

    private FeedResponse toFeed(Postagem p, String dispositivoId) {
        boolean curtidoPorMim = StringUtils.hasText(dispositivoId)
                && curtidaRepository.existsByPostagemIdAndDispositivoId(p.getId(), dispositivoId);
        return new FeedResponse(
                p.getId(),
                p.getTitulo(),
                p.getDescricao(),
                new Ref(p.getUnidadeSaude().getId(), p.getUnidadeSaude().getNome()),
                storageService.urlVisualizacao(p.getUrl(), PostagemController.VALIDADE_IMAGEM),
                p.isMostrarTotalCurtidas(),
                curtidaRepository.countByPostagemId(p.getId()),
                p.isHabilitarComentarios(),
                comentarioRepository.countByPostagemId(p.getId()),
                curtidoPorMim,
                p.getCriadoEm());
    }
}
