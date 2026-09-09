package com.example.pop.postagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.Configuracao;
import com.example.pop.configuracao.ConfiguracaoRepository;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.usuario.UsuarioRepository;
import com.example.pop.verificacao.VerificacaoService;

@SpringBootTest
class PostagemControllerTest {

    private static final String IMG = "http://s3/portal-paciente/prontuarios/teste-postagem.jpg";
    private static final String TEL = "11922221111";

    @Autowired
    private PostagemController controller;
    @Autowired
    private FeedController feedController;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private ComentarioRepository comentarioRepository;
    @Autowired
    private PostagemRepository postagemRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ConfiguracaoRepository configuracaoRepository;
    @Autowired
    private ConfiguracaoService configuracaoService;
    @Autowired
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private VerificacaoService verificacao;

    private Long pacienteId;
    /** Token do paciente logado — comentar/responder derivam o autor dele. */
    private Jwt jwt;

    /** Token de admin com o claim "uid" (um usuário existente, p/ respeitar a FK). */
    private Jwt adminJwt() {
        Long uid = usuarioRepository.findAll().get(0).getId();
        return Jwt.withTokenValue("t").header("alg", "none").claim("uid", uid).build();
    }

    @BeforeEach
    void setup() {
        // Configs da rede social no PADRÃO a cada teste — isola os testes que as alteram
        // (evita que um deixe OCULTAR/IDADE mudado e quebre a asserção de outro).
        definirOcultarNomeUsuario(true);
        definirIdadeMinimaComentarios(0);
        limparPacienteDeTeste(TEL);
        // Vincula o paciente à unidade 1 (as postagens dos testes usam unidadeSaudeId=1).
        pacienteId = pacienteController.criar(new PacienteRequest("Joao Teste", TEL, java.util.List.of(1L)), null).getId();
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        String token = authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-post")).token();
        jwt = jwtDecoder.decode(token);
    }

    @AfterEach
    void limpar() {
        limparPacienteDeTeste(TEL);
    }

    /**
     * Remove o paciente de teste de forma segura: apaga antes as postagens que carregam
     * comentários dele (o ON DELETE CASCADE limpa os comentários), senão a FK
     * comentario→paciente impede o delete quando um teste falhou antes do próprio excluir.
     */
    private void limparPacienteDeTeste(String telefone) {
        pacienteRepository.findByTelefone(telefone).ifPresent(p -> {
            var postagens = comentarioRepository.findByPacienteId(p.getId()).stream()
                    .map(c -> c.getPostagem().getId()).distinct().toList();
            if (!postagens.isEmpty()) {
                postagemRepository.deleteAllById(postagens);
            }
            pacienteRepository.deleteById(p.getId());
        });
    }

    @Test
    void fluxoCriarFeedCurtirComentar() {
        PostagemDetalheResponse criada = controller.criar(new PostagemRequest(
                "Campanha de vacinação", "Venha se vacinar na sua unidade!", true, true, 1L, IMG));
        Long id = criada.id();
        assertNotNull(id);
        assertEquals("Campanha de vacinação", criada.titulo());

        // Aparece no feed
        Pagina<FeedResponse> feed = feedController.feed(jwt, "dev-teste-1", 0, 50);
        assertTrue(feed.content().stream().anyMatch(f -> f.id().equals(id)));

        // Detalhe da postagem (tela de detalhe do app)
        FeedResponse detalhe = feedController.postagem(jwt, id, "dev-teste-1");
        assertEquals("Campanha de vacinação", detalhe.titulo());
        assertFalse(detalhe.curtidoPorMim());

        // Curtir e descurtir (toggle)
        CurtirResponse r1 = feedController.curtir(id, new CurtirRequest("dev-teste-1"));
        assertTrue(r1.curtido());
        assertEquals(1, r1.totalCurtidas());
        CurtirResponse r2 = feedController.curtir(id, new CurtirRequest("dev-teste-1"));
        assertFalse(r2.curtido());
        assertEquals(0, r2.totalCurtidas());

        // curtidoPorMim reflete o aparelho
        feedController.curtir(id, new CurtirRequest("dev-teste-2"));
        FeedResponse meu = feedController.feed(jwt, "dev-teste-2", 0, 50).content().stream()
                .filter(f -> f.id().equals(id)).findFirst().orElseThrow();
        assertTrue(meu.curtidoPorMim());
        assertEquals(1, meu.totalCurtidas());

        // Comentar — o autor é resolvido pelo id do paciente (nome abreviado por
        // NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL, ligado por padrão: "J. T."), nunca vindo do corpo.
        ComentarioResponse c = feedController.comentar(id, new ComentarRequest("Muito bom!"), jwt);
        assertEquals("J. T.", c.autor());
        assertTrue(c.meu(), "o comentário recém-criado é do paciente logado");
        assertFalse(c.editado());
        Pagina<ComentarioResponse> lista = feedController.comentarios(id, 0, 20, jwt);
        assertEquals(1, lista.totalElements());
        assertTrue(lista.content().get(0).meu());

        // Excluir (cascade remove curtidas/comentários)
        controller.excluir(id);
        assertEquals(404, controller.buscar(id).getStatusCode().value());
    }

    @Test
    void respostasDeComentario() {
        PostagemDetalheResponse criada = controller.criar(new PostagemRequest(
                "Evento de saúde", "Participe!", true, true, 1L, IMG));
        Long id = criada.id();

        // Comentário-raiz do paciente
        ComentarioResponse raiz = feedController.comentar(id,
                new ComentarRequest("Que dia será esse evento?"), jwt);
        assertTrue(raiz.respostas().isEmpty());

        // Administração responde (lado admin: autor = "Administração", resolvido pelo usuarioId)
        ComentarioResponse respAdmin = controller
                .responderComentario(raiz.id(), new ComentarRequest("Será dia 10"), adminJwt()).getBody();
        assertNotNull(respAdmin);
        assertEquals("Administração", respAdmin.autor());

        // Paciente responde no mesmo comentário-raiz
        feedController.responder(id, raiz.id(), new ComentarRequest("Também quero saber"), jwt);

        // Responder a uma resposta continua ancorado na raiz (threading de 1 nível)
        feedController.responder(id, respAdmin.id(), new ComentarRequest("Qual horário?"), jwt);

        // Listagem: 1 comentário-raiz com 3 respostas
        Pagina<ComentarioResponse> pagina = feedController.comentarios(id, 0, 20, jwt);
        assertEquals(1, pagina.totalElements());
        assertEquals(3, pagina.content().get(0).respostas().size());

        // Total de comentários (raiz + respostas)
        FeedResponse detalhe = feedController.postagem(jwt, id, "dev-x");
        assertEquals(4, detalhe.totalComentarios());

        controller.excluir(id);
        assertEquals(404, controller.buscar(id).getStatusCode().value());
    }

    @Test
    void comentarioBloqueadoQuandoDesabilitado() {
        PostagemDetalheResponse criada = controller.criar(new PostagemRequest(
                "Aviso importante", "Sem comentários", true, false, 1L, IMG));
        Long id = criada.id();
        assertThrows(ResponseStatusException.class,
                () -> feedController.comentar(id, new ComentarRequest("oi"), jwt));
        controller.excluir(id);
    }

    @Test
    void editarProprioComentarioDentroDaJanela() {
        Long id = controller.criar(new PostagemRequest("Dica", "Beba água", true, true, 1L, IMG)).id();
        ComentarioResponse c = feedController.comentar(id, new ComentarRequest("otimo"), jwt);
        assertFalse(c.editado());

        ComentarioResponse editado = feedController.editar(id, c.id(),
                new EditarComentarioRequest("ótimo, obrigado!"), jwt);
        assertEquals("ótimo, obrigado!", editado.texto());
        assertTrue(editado.editado());
        assertTrue(editado.meu());

        controller.excluir(id);
    }

    @Test
    void naoEditaComentarioForaDaJanelaMasAindaExclui() {
        Long id = controller.criar(new PostagemRequest("Aviso", "texto", true, true, 1L, IMG)).id();
        ComentarioResponse c = feedController.comentar(id, new ComentarRequest("antigo"), jwt);
        // Envelhece o comentário além dos 15 min.
        Comentario entidade = comentarioRepository.findById(c.id()).orElseThrow();
        entidade.setCriadoEm(LocalDateTime.now().minusMinutes(20));
        comentarioRepository.save(entidade);

        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> feedController.editar(id, c.id(), new EditarComentarioRequest("novo"), jwt))
                .getStatusCode().value());
        // Excluir não tem prazo.
        feedController.excluir(id, c.id(), jwt);
        assertEquals(0, feedController.comentarios(id, 0, 20, jwt).totalElements());

        controller.excluir(id);
    }

    @Test
    void excluirComentarioRaizRemoveTodasAsRespostas() {
        Long id = controller.criar(new PostagemRequest("Mutirão", "Sábado", true, true, 1L, IMG)).id();
        ComentarioResponse raiz = feedController.comentar(id, new ComentarRequest("que horas?"), jwt);
        feedController.responder(id, raiz.id(), new ComentarRequest("eu também"), jwt);
        controller.responderComentario(raiz.id(), new ComentarRequest("às 9h"), adminJwt()); // resposta de outro
        assertEquals(3, feedController.postagem(jwt, id, "dev-x").totalComentarios());

        // O dono exclui o raiz → apaga o raiz e TODAS as respostas (inclusive a do admin).
        feedController.excluir(id, raiz.id(), jwt);
        assertEquals(0, feedController.comentarios(id, 0, 20, jwt).totalElements());
        assertEquals(0, feedController.postagem(jwt, id, "dev-x").totalComentarios());

        controller.excluir(id);
    }

    @Test
    void soODonoPodeEditarOuExcluir() {
        String tel2 = "11933332222";
        pacienteRepository.findByTelefone(tel2).ifPresent(p -> pacienteRepository.deleteById(p.getId()));
        Long outroId = pacienteController.criar(new PacienteRequest("Maria Outra", tel2), null).getId();
        Jwt jwtOutro = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(tel2, "000000", "dev-outro")).token());

        Long id = controller.criar(new PostagemRequest("Regras", "teste", true, true, 1L, IMG)).id();
        ComentarioResponse c = feedController.comentar(id, new ComentarRequest("meu comentário"), jwt);

        // Outro paciente não pode editar nem excluir (403).
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> feedController.editar(id, c.id(), new EditarComentarioRequest("hack"), jwtOutro))
                .getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> feedController.excluir(id, c.id(), jwtOutro)).getStatusCode().value());
        // Para o outro paciente, o comentário não é "meu".
        assertFalse(feedController.comentarios(id, 0, 20, jwtOutro).content().get(0).meu());

        controller.excluir(id);
        pacienteRepository.deleteById(outroId);
    }

    @Test
    void adminEditaOProprioComentarioDentroDaJanela() {
        Long id = controller.criar(new PostagemRequest("Aviso admin", "texto", true, true, 1L, IMG)).id();
        ComentarioResponse raiz = feedController.comentar(id, new ComentarRequest("dúvida"), jwt);
        ComentarioResponse resp = controller
                .responderComentario(raiz.id(), new ComentarRequest("resposta"), adminJwt()).getBody();
        assertNotNull(resp);
        assertTrue(resp.meu(), "a resposta é do admin logado");
        assertFalse(resp.editado());

        ComentarioResponse editado = controller.editarComentario(resp.id(),
                new EditarComentarioRequest("resposta corrigida"), adminJwt());
        assertEquals("resposta corrigida", editado.texto());
        assertTrue(editado.editado());

        // Outro admin (uid diferente) não pode editar.
        Jwt outroAdmin = Jwt.withTokenValue("t").header("alg", "none").claim("uid", 999999L).build();
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.editarComentario(resp.id(), new EditarComentarioRequest("hack"), outroAdmin))
                .getStatusCode().value());

        controller.excluir(id);
    }

    @Test
    void filtraPorTitulo() {
        PostagemDetalheResponse criada = controller.criar(new PostagemRequest(
                "Semana da Saúde 2026", "Programação especial", false, true, 1L, IMG));
        Pagina<PostagemResponse> pagina = controller.listar("Semana da Saúde", null, null, null, 0, 10);
        assertTrue(pagina.content().stream().anyMatch(p -> p.id().equals(criada.id())));
        controller.excluir(criada.id());
    }

    @Test
    void statusComentarioNovoMarcaEZeraAoAbrir() {
        Long id = controller.criar(new PostagemRequest("Novidade", "texto", true, true, 1L, IMG)).id();

        // Sem comentário → não é novo.
        assertFalse(novoNaLista(id), "recém-criada não deve estar como 'novo comentário'");

        // Paciente comenta → vira "novo comentário".
        feedController.comentar(id, new ComentarRequest("primeiro!"), jwt);
        assertTrue(novoNaLista(id), "comentário de paciente marca a postagem como nova");
        // Filtro "com novos" traz; "sem novos" não traz.
        assertTrue(controller.listar(null, null, null, true, 0, 100).content().stream().anyMatch(p -> p.id().equals(id)));
        assertFalse(controller.listar(null, null, null, false, 0, 100).content().stream().anyMatch(p -> p.id().equals(id)));

        // Admin abre a postagem (buscar lista os comentários) → zera o status.
        assertEquals(200, controller.buscar(id).getStatusCode().value());
        assertFalse(novoNaLista(id), "abrir a postagem zera o status");

        // Resposta do próprio admin NÃO remarca; resposta de paciente remarca.
        var raiz = feedController.comentarios(id, 0, 20, jwt).content().get(0);
        controller.responderComentario(raiz.id(), new ComentarRequest("obrigado"), adminJwt());
        assertFalse(novoNaLista(id), "resposta do admin não marca como novo");
        feedController.responder(id, raiz.id(), new ComentarRequest("de nada"), jwt);
        assertTrue(novoNaLista(id), "resposta de paciente marca como novo");

        controller.excluir(id);
    }

    @Test
    void nomeDoUsuarioNaRedeSocialConformeConfig() {
        Long id = controller.criar(new PostagemRequest("Config autor", "texto", true, true, 1L, IMG)).id();
        ComentarioResponse raiz = feedController.comentar(id, new ComentarRequest("pergunta"), jwt);
        Long adminUid = usuarioRepository.findAll().get(0).getId();
        String nomeAdmin = usuarioRepository.findById(adminUid).orElseThrow().getNome();
        Jwt admin = Jwt.withTokenValue("t").header("alg", "none").claim("uid", adminUid).build();
        controller.responderComentario(raiz.id(), new ComentarRequest("resposta oficial"), admin);

        // Padrão (OCULTAR ligado): comentário da unidade aparece como "Administração".
        assertEquals("Administração",
                feedController.comentarios(id, 0, 20, jwt).content().get(0).respostas().get(0).autor());

        try {
            definirOcultarNomeUsuario(false);
            // Config desligada: aparece o nome COMPLETO do usuário que comentou.
            assertEquals(nomeAdmin,
                    feedController.comentarios(id, 0, 20, jwt).content().get(0).respostas().get(0).autor());
        } finally {
            definirOcultarNomeUsuario(true); // restaura para não afetar os outros testes
        }

        controller.excluir(id);
    }

    @Test
    void idadeMinimaParaComentarNaRedeSocial() {
        Long id = controller.criar(new PostagemRequest("Idade mínima", "texto", true, true, 1L, IMG)).id();
        try {
            definirIdadeMinimaComentarios(18);

            // Paciente sem data de nascimento → bloqueia (422) pedindo o cadastro.
            ResponseStatusException semData = assertThrows(ResponseStatusException.class,
                    () -> feedController.comentar(id, new ComentarRequest("oi"), jwt));
            assertEquals(422, semData.getStatusCode().value());

            // Menor de idade → continua bloqueado (422).
            definirDataNascimentoPaciente(LocalDate.now().minusYears(10));
            assertEquals(422, assertThrows(ResponseStatusException.class,
                    () -> feedController.comentar(id, new ComentarRequest("oi"), jwt)).getStatusCode().value());

            // Com idade suficiente → comenta normalmente.
            definirDataNascimentoPaciente(LocalDate.now().minusYears(20));
            ComentarioResponse ok = feedController.comentar(id, new ComentarRequest("agora vai"), jwt);
            assertNotNull(ok.id());

            // Editar também respeita a idade (republica conteúdo): vira menor → bloqueia.
            definirDataNascimentoPaciente(LocalDate.now().minusYears(10));
            assertEquals(422, assertThrows(ResponseStatusException.class,
                    () -> feedController.editar(id, ok.id(), new EditarComentarioRequest("novo texto"), jwt))
                    .getStatusCode().value());
        } finally {
            definirIdadeMinimaComentarios(0); // restaura (desliga a restrição p/ os demais testes)
        }
        controller.excluir(id);
    }

    /** Define o valor da config de idade mínima e invalida o cache. */
    private void definirIdadeMinimaComentarios(int anos) {
        Configuracao c = configuracaoRepository
                .findByChave(ChaveConfiguracao.IDADE_MINIMA_COMENTARIOS_REDES_SOCIAIS).orElseThrow();
        c.setValorNumerico(BigDecimal.valueOf(anos));
        configuracaoRepository.save(c);
        configuracaoService.invalidarCache();
    }

    /** Ajusta a data de nascimento do paciente de teste. */
    private void definirDataNascimentoPaciente(LocalDate data) {
        Paciente p = pacienteRepository.findById(pacienteId).orElseThrow();
        p.setDataNascimento(data);
        pacienteRepository.save(p);
    }

    /** Liga/desliga OCULTAR_NOME_USUARIO_NA_REDESOCIAL e invalida o cache da config. */
    private void definirOcultarNomeUsuario(boolean ocultar) {
        Configuracao c = configuracaoRepository.findByChave(ChaveConfiguracao.OCULTAR_NOME_USUARIO_NA_REDESOCIAL)
                .orElseThrow();
        c.setValorBooleano(ocultar);
        configuracaoRepository.save(c);
        configuracaoService.invalidarCache();
    }

    /** True se a postagem aparece com "novo comentário" na listagem do admin. */
    private boolean novoNaLista(Long id) {
        return controller.listar(null, null, null, null, 0, 100).content().stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .map(PostagemResponse::novoComentario)
                .orElse(false);
    }
}
