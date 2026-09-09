package com.example.pop.postagem;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.notificacaoadmin.NotificacaoAdminService;
import com.example.pop.notificacaoadmin.TipoNotificacaoAdmin;
import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.Responsavel;
import com.example.pop.storage.StorageService;

import jakarta.validation.Valid;

/** Endpoints da rede social usados pelo app (feed, curtir, comentar). */
@RestController
public class FeedController {

    private static final int TAMANHO_MAXIMO = 100;
    /** Janela em que o autor ainda pode editar o próprio comentário (fonte única no response). */
    private static final int JANELA_EDICAO_MIN = ComentarioResponse.JANELA_EDICAO_MINUTOS;
    /** Validade da URL da foto do autor exibida junto ao comentário. */
    private static final Duration VALIDADE_FOTO = Duration.ofHours(6);

    private final PostagemRepository repository;
    private final CurtidaRepository curtidaRepository;
    private final ComentarioRepository comentarioRepository;
    private final StorageService storageService;
    private final PacienteAcessoService acessoService;
    private final PacienteRepository pacienteRepository;
    private final ModeracaoService moderacaoService;
    private final NotificacaoAdminService notificacaoAdminService;
    private final ConfiguracaoService configuracaoService;
    private final AutorComentarioService autorService;

    public FeedController(PostagemRepository repository, CurtidaRepository curtidaRepository,
            ComentarioRepository comentarioRepository, StorageService storageService,
            PacienteAcessoService acessoService, PacienteRepository pacienteRepository,
            ModeracaoService moderacaoService, NotificacaoAdminService notificacaoAdminService,
            ConfiguracaoService configuracaoService, AutorComentarioService autorService) {
        this.repository = repository;
        this.curtidaRepository = curtidaRepository;
        this.comentarioRepository = comentarioRepository;
        this.storageService = storageService;
        this.acessoService = acessoService;
        this.pacienteRepository = pacienteRepository;
        this.moderacaoService = moderacaoService;
        this.notificacaoAdminService = notificacaoAdminService;
        this.configuracaoService = configuracaoService;
        this.autorService = autorService;
    }

    /**
     * Minutos da janela de edição de comentário — vem da configuração
     * {@code MINUTOS_PARA_EDITAR_COMENTARIO}; cai no padrão se o valor estiver vazio.
     */
    private int janelaEdicaoMinutos() {
        try {
            BigDecimal minutos = configuracaoService.lerNumerico(ChaveConfiguracao.MINUTOS_PARA_EDITAR_COMENTARIO);
            return minutos == null ? JANELA_EDICAO_MIN : minutos.intValue();
        } catch (RuntimeException e) {
            // Config ausente/indisponível: usa o padrão — nunca derruba o feed (caminho público).
            return JANELA_EDICAO_MIN;
        }
    }

    /**
     * Exige a idade mínima para comentar na rede social (config
     * {@code IDADE_MINIMA_COMENTARIOS_REDES_SOCIAIS}). Libera se o PACIENTE ou o RESPONSÁVEL
     * (quando comenta por ele) tiver ao menos a idade mínima; sem data de nascimento para
     * validar, bloqueia (422). 0 = sem restrição.
     */
    private void exigirIdadeParaComentar(Paciente paciente, Responsavel responsavel) {
        int minima = idadeMinimaComentarios();
        if (minima <= 0) {
            return; // config desligada: sem restrição de idade
        }
        if (atingeIdade(paciente.getDataNascimento(), minima)
                || (responsavel != null && atingeIdade(responsavel.getDataNascimento(), minima))) {
            return; // paciente OU responsável atinge a idade mínima
        }
        // Bloqueado: sem NENHUMA data de nascimento para validar → pede o cadastro; senão, idade.
        boolean semData = paciente.getDataNascimento() == null
                && (responsavel == null || responsavel.getDataNascimento() == null);
        String mensagem = semData
                ? "Cadastre a data de nascimento para poder comentar na rede social."
                : "É necessário ter ao menos " + minima + " anos para comentar na rede social.";
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }

    /** true se a data de nascimento existe e a idade (em anos completos) é >= à mínima. */
    private boolean atingeIdade(LocalDate nascimento, int minima) {
        return nascimento != null && Period.between(nascimento, LocalDate.now()).getYears() >= minima;
    }

    /**
     * Idade mínima para comentar — config {@code IDADE_MINIMA_COMENTARIOS_REDES_SOCIAIS};
     * 0 (sem restrição) se ausente/vazia/indisponível (fail-open: config quebrada não trava
     * os comentários de todo mundo).
     */
    private int idadeMinimaComentarios() {
        try {
            BigDecimal anos = configuracaoService.lerNumerico(ChaveConfiguracao.IDADE_MINIMA_COMENTARIOS_REDES_SOCIAIS);
            return anos == null ? 0 : anos.intValue();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @GetMapping("/feed")
    public Pagina<FeedResponse> feed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String dispositivoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Feed do paciente logado: só as postagens das unidades a que ele tem acesso.
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.REDE_SOCIAL);
        Set<Long> unidades = acessoService.unidadeIdsDoPaciente(paciente);
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String disp = dispositivoId == null ? "" : dispositivoId.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm"));
        // Sem unidade vinculada = feed vazio (regra estrita).
        Page<Postagem> resultado = unidades.isEmpty()
                ? Page.empty(pageable)
                : repository.findByUnidadeSaude_IdInOrderByCriadoEmDesc(unidades, pageable);
        List<FeedResponse> content = resultado.getContent().stream()
                .map(p -> toFeed(p, disp, paciente.getId())).toList();

        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Detalhe de uma postagem no formato do feed (para a tela de detalhe do app). */
    @GetMapping("/feed/{id}")
    public FeedResponse postagem(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestParam(required = false) String dispositivoId) {
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.REDE_SOCIAL);
        Postagem postagem = obter(id);
        // Não deixa abrir por id uma postagem de unidade fora do acesso do paciente.
        acessoService.exigirUnidade(paciente, postagem.getUnidadeSaude().getId());
        return toFeed(postagem, dispositivoId == null ? "" : dispositivoId.trim(), paciente.getId());
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
        // Admin vê todos os comentários (inclusive pendentes/rejeitados para moderar); o público
        // e o paciente veem só os publicados + os próprios pendentes ("em análise"). Filtrar no
        // banco mantém a paginação correta (não conta/expõe comentários ocultos).
        Page<Comentario> resultado = adminAtual != null
                ? comentarioRepository.findByPostagemIdAndComentarioPaiIsNullOrderByCriadoEmDesc(id, pageable)
                : comentarioRepository.findRaizesVisiveis(id, pacienteAtual, pageable);

        // Carrega as respostas dos comentários-raiz desta página em uma única consulta.
        List<Comentario> raizesList = resultado.getContent();
        List<Long> raizes = raizesList.stream().map(Comentario::getId).toList();
        Map<Long, List<Comentario>> porPai = raizes.isEmpty()
                ? Map.of()
                : comentarioRepository.findByComentarioPaiIdInOrderByCriadoEmAsc(raizes).stream()
                        .collect(Collectors.groupingBy(r -> r.getComentarioPai().getId()));

        // Fotos dos autores só para leitor AUTENTICADO: o feed é público e o nome já é
        // anonimizado por LGPD ("Mariana D."), então não expomos o rosto a acesso anônimo.
        // (raízes + respostas resolvidas de uma vez, quando há foto a resolver.)
        List<Comentario> todos = new ArrayList<>(raizesList);
        porPai.values().forEach(todos::addAll);
        boolean autenticado = pacienteAtual != null || adminAtual != null;
        Function<Long, String> fotoDoPaciente = autenticado ? resolverFotos(todos) : pid -> null;

        // Nome exibido resolvido pelos ids (paciente/usuário) em tempo de leitura, e nome do
        // responsável pelo responsavelId — ambos abreviados conforme NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL.
        Function<Comentario, String> autorDoComentario = autorService.autores(todos);
        Function<Long, String> nomeDoResponsavel = autorService.nomesResponsavel(todos);
        int janela = janelaEdicaoMinutos();
        // As raízes já vêm filtradas do banco; aqui filtram-se as RESPOSTAS ocultas (ex.: resposta
        // pendente de outro paciente sob um comentário publicado).
        List<ComentarioResponse> content = raizesList.stream()
                .map(c -> {
                    List<Comentario> respostas = porPai.getOrDefault(c.getId(), List.of()).stream()
                            .filter(r -> visivel(r, pacienteAtual, adminAtual))
                            .toList();
                    return ComentarioResponse.from(c, respostas, pacienteAtual, adminAtual,
                            autorDoComentario, fotoDoPaciente, nomeDoResponsavel, janela);
                })
                .toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @PostMapping("/postagem/{id}/comentarios")
    public ComentarioResponse comentar(@PathVariable Long id, @Valid @RequestBody ComentarRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        acessoService.exigirLancar(jwt, FuncionalidadeApp.REDE_SOCIAL);
        Postagem postagem = obter(id);
        if (!postagem.isHabilitarComentarios()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comentários desativados para esta postagem");
        }
        // Revalida a sessão (ativo + aparelho vinculado) e usa o nome do paciente
        // validado — autor confiável, nunca vindo do corpo.
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        acessoService.exigirUnidade(paciente, postagem.getUnidadeSaude().getId());
        Responsavel responsavel = acessoService.responsavelDaSessao(jwt).orElse(null);
        exigirIdadeParaComentar(paciente, responsavel);
        Comentario comentario = new Comentario();
        comentario.setPostagem(postagem);
        comentario.setPacienteId(paciente.getId());
        if (responsavel != null) {
            comentario.setResponsavelId(responsavel.getId());
        }
        comentario.setTexto(request.texto().trim());
        comentario.setCriadoEm(LocalDateTime.now());
        moderarSeNecessario(postagem, comentario);
        Comentario salvo = comentarioRepository.save(comentario);
        marcarComentarioNovo(postagem);
        notificarModeracaoSePendente(postagem, salvo);
        String autor = autorService.autorPaciente(paciente.getNome());
        String nomeResponsavel = autorService.nomeResponsavel(responsavel);
        return ComentarioResponse.from(salvo, paciente.getId(), null, c -> autor, umaFoto(paciente),
                rid -> nomeResponsavel, janelaEdicaoMinutos());
    }

    /**
     * Responde a um comentário (outro paciente pode ajudar a tirar a dúvida). Sem
     * {@code @Transactional} de propósito: a moderação por IA faz uma chamada HTTP (até o
     * timeout) e não pode segurar uma conexão do pool aberta. O pai vem com o comentário-raiz
     * já inicializado ({@code findByIdComPai}) para navegar até a raiz fora de transação.
     */
    @PostMapping("/postagem/{id}/comentarios/{comentarioId}/responder")
    public ComentarioResponse responder(@PathVariable Long id, @PathVariable Long comentarioId,
            @Valid @RequestBody ComentarRequest request, @AuthenticationPrincipal Jwt jwt) {
        acessoService.exigirLancar(jwt, FuncionalidadeApp.REDE_SOCIAL);
        Postagem postagem = obter(id);
        if (!postagem.isHabilitarComentarios()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comentários desativados para esta postagem");
        }
        Comentario pai = comentarioRepository.findByIdComPai(comentarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentário não encontrado"));
        if (!pai.getPostagem().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comentário não pertence à postagem");
        }
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        acessoService.exigirUnidade(paciente, postagem.getUnidadeSaude().getId());
        Responsavel responsavel = acessoService.responsavelDaSessao(jwt).orElse(null);
        exigirIdadeParaComentar(paciente, responsavel);
        // Threading de 1 nível: a resposta se ancora sempre no comentário-raiz.
        Comentario raiz = pai.getComentarioPai() != null ? pai.getComentarioPai() : pai;
        Comentario resposta = new Comentario();
        resposta.setPostagem(postagem);
        resposta.setComentarioPai(raiz);
        resposta.setPacienteId(paciente.getId());
        if (responsavel != null) {
            resposta.setResponsavelId(responsavel.getId());
        }
        resposta.setTexto(request.texto().trim());
        resposta.setCriadoEm(LocalDateTime.now());
        moderarSeNecessario(postagem, resposta);
        Comentario salva = comentarioRepository.save(resposta);
        marcarComentarioNovo(postagem);
        notificarModeracaoSePendente(postagem, salva);
        String autor = autorService.autorPaciente(paciente.getNome());
        String nomeResponsavel = autorService.nomeResponsavel(responsavel);
        return ComentarioResponse.from(salva, paciente.getId(), null, c -> autor, umaFoto(paciente),
                rid -> nomeResponsavel, janelaEdicaoMinutos());
    }

    /**
     * Edita o próprio comentário — permitido só dentro da janela configurável
     * ({@code MINUTOS_PARA_EDITAR_COMENTARIO}) após criar. Sem {@code @Transactional}
     * (a moderação faz chamada HTTP; não segura conexão do pool).
     */
    @PutMapping("/postagem/{id}/comentarios/{comentarioId}")
    public ComentarioResponse editar(@PathVariable Long id, @PathVariable Long comentarioId,
            @Valid @RequestBody EditarComentarioRequest request, @AuthenticationPrincipal Jwt jwt) {
        acessoService.exigirLancar(jwt, FuncionalidadeApp.REDE_SOCIAL);
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        Comentario c = comentarioDaPostagem(id, comentarioId);
        exigirDono(c, paciente);
        // Editar republica conteúdo: aplica a mesma trava de idade mínima do comentar/responder
        // (um menor não reescreve o próprio comentário para conteúdo novo).
        exigirIdadeParaComentar(paciente, acessoService.responsavelDaSessao(jwt).orElse(null));
        int janela = janelaEdicaoMinutos();
        if (c.getCriadoEm().isBefore(LocalDateTime.now().minusMinutes(janela))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O prazo para editar este comentário (" + janela + " min) expirou.");
        }
        c.setTexto(request.texto().trim());
        c.setEditadoEm(LocalDateTime.now());
        // Segurança: um comentário já publicado pode ser reescrito para conteúdo ofensivo dentro
        // da janela de edição. Se a postagem exige validação por IA, revalida o texto editado.
        remoderarAoEditar(c);
        Comentario salvo = comentarioRepository.save(c);
        notificarModeracaoSePendente(salvo.getPostagem(), salvo);
        return ComentarioResponse.from(salvo, paciente.getId(), null, autorService.autores(List.of(salvo)),
                umaFoto(paciente), autorService.nomesResponsavel(List.of(salvo)), janela);
    }

    /**
     * Exclui o próprio comentário (sem prazo). Se for um comentário-raiz, apaga
     * também todas as respostas abaixo — inclusive de outros pacientes.
     */
    @DeleteMapping("/postagem/{id}/comentarios/{comentarioId}")
    @Transactional
    public ResponseEntity<Void> excluir(@PathVariable Long id, @PathVariable Long comentarioId,
            @AuthenticationPrincipal Jwt jwt) {
        acessoService.exigirLancar(jwt, FuncionalidadeApp.REDE_SOCIAL);
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

    /**
     * Se a postagem exige validação por IA, modera o comentário antes de publicar. Ofensivo
     * (ou indeterminado, por falha da IA) → PENDENTE (oculto do público até o admin decidir).
     */
    private void moderarSeNecessario(Postagem postagem, Comentario comentario) {
        if (!postagem.isValidarComentariosIa()) {
            return;
        }
        ModeracaoService.Moderacao m = moderacaoService.avaliar(comentario.getTexto());
        if (!m.liberado()) {
            comentario.setStatusModeracao(StatusModeracao.PENDENTE);
            comentario.setMotivoModeracao(m.motivo());
        }
    }

    /**
     * Revalida por IA um comentário que foi EDITADO, quando a postagem exige validação. Nunca
     * republica sozinho um comentário que o admin já havia REJEITADO (vai para nova análise);
     * nos demais casos, texto liberado → PUBLICADO, ofensivo/indeterminado → PENDENTE.
     */
    private void remoderarAoEditar(Comentario c) {
        if (!c.getPostagem().isValidarComentariosIa()) {
            return;
        }
        boolean eraRejeitado = c.getStatusModeracao() == StatusModeracao.REJEITADO;
        ModeracaoService.Moderacao m = moderacaoService.avaliar(c.getTexto());
        if (m.liberado() && !eraRejeitado) {
            c.setStatusModeracao(StatusModeracao.PUBLICADO);
            c.setMotivoModeracao(null);
        } else {
            c.setStatusModeracao(StatusModeracao.PENDENTE);
            c.setMotivoModeracao(m.liberado()
                    ? "Comentário reprovado foi editado; aguardando nova revisão."
                    : m.motivo());
        }
    }

    /**
     * Se o comentário ficou PENDENTE (retido pela IA), avisa o back-office (sino do admin):
     * conteúdo oculto do público aguardando aprovar/rejeitar. Best-effort e sem transação em
     * volta (o comentário já foi salvo), então não segura conexão nem deixa notificação órfã.
     */
    private void notificarModeracaoSePendente(Postagem postagem, Comentario c) {
        if (c.getStatusModeracao() != StatusModeracao.PENDENTE) {
            return;
        }
        notificacaoAdminService.registrar(TipoNotificacaoAdmin.MODERACAO,
                postagem.getUnidadeSaude().getId(),
                "Comentário para revisar",
                "Um comentário foi retido para moderação e aguarda sua aprovação.",
                c.getId(), "/postagens/" + postagem.getId() + "#coment-" + c.getId());
    }

    /** Visível ao leitor: admin vê tudo; público só PUBLICADO; o autor vê o próprio PENDENTE. */
    private boolean visivel(Comentario c, Long pacienteAtual, Long adminAtual) {
        if (adminAtual != null) {
            return true;
        }
        if (c.getStatusModeracao() == StatusModeracao.PUBLICADO) {
            return true;
        }
        return c.getStatusModeracao() == StatusModeracao.PENDENTE
                && pacienteAtual != null && pacienteAtual.equals(c.getPacienteId());
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
     * Resolve a foto (URL pré-assinada) de cada autor pelo {@code pacienteId}, buscando
     * os pacientes de uma vez. Sem paciente (comentário do admin/antigo) ou sem foto → null.
     */
    private Function<Long, String> resolverFotos(List<Comentario> comentarios) {
        Set<Long> ids = comentarios.stream().map(Comentario::getPacienteId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return pid -> null;
        }
        Map<Long, String> fotos = pacienteRepository.findAllById(ids).stream()
                .filter(p -> p.getFotoUrl() != null)
                .collect(Collectors.toMap(Paciente::getId,
                        p -> storageService.urlVisualizacao(p.getFotoUrl(), VALIDADE_FOTO)));
        return pid -> pid == null ? null : fotos.get(pid);
    }

    /** Resolver de foto para um único comentário/resposta do paciente logado. */
    private Function<Long, String> umaFoto(Paciente paciente) {
        String foto = storageService.urlVisualizacao(paciente.getFotoUrl(), VALIDADE_FOTO);
        return pid -> foto;
    }

    private Postagem obter(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postagem não encontrada"));
    }

    private FeedResponse toFeed(Postagem p, String dispositivoId, Long pacienteId) {
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
                // Conta só o que o paciente pode ver (não vaza pendentes de outros / rejeitados).
                comentarioRepository.countVisiveis(p.getId(), pacienteId),
                curtidoPorMim,
                p.getCriadoEm());
    }
}
