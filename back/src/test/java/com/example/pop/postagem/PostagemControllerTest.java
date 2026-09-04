package com.example.pop.postagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.usuario.UsuarioRepository;

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
    private UsuarioRepository usuarioRepository;
    @Autowired
    private JwtDecoder jwtDecoder;

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
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> pacienteRepository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Joao Teste", TEL)).getId();
        String codigo = pacienteController.gerarCodigo(pacienteId).getBody().codigo();
        String token = authController.ativar(new AtivarPacienteRequest(TEL, codigo, "dev-post")).token();
        jwt = jwtDecoder.decode(token);
    }

    @AfterEach
    void limpar() {
        pacienteRepository.deleteById(pacienteId);
    }

    @Test
    void fluxoCriarFeedCurtirComentar() {
        PostagemDetalheResponse criada = controller.criar(new PostagemRequest(
                "Campanha de vacinação", "Venha se vacinar na sua unidade!", true, true, 1L, IMG));
        Long id = criada.id();
        assertNotNull(id);
        assertEquals("Campanha de vacinação", criada.titulo());

        // Aparece no feed
        Pagina<FeedResponse> feed = feedController.feed("dev-teste-1", 0, 50);
        assertTrue(feed.content().stream().anyMatch(f -> f.id().equals(id)));

        // Detalhe da postagem (tela de detalhe do app)
        FeedResponse detalhe = feedController.postagem(id, "dev-teste-1");
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
        FeedResponse meu = feedController.feed("dev-teste-2", 0, 50).content().stream()
                .filter(f -> f.id().equals(id)).findFirst().orElseThrow();
        assertTrue(meu.curtidoPorMim());
        assertEquals(1, meu.totalCurtidas());

        // Comentar — o autor vem do token do paciente (primeiro nome + inicial), não do corpo
        ComentarioResponse c = feedController.comentar(id, new ComentarRequest("João", "Muito bom!"), jwt);
        assertEquals("Joao T.", c.autor());
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
                new ComentarRequest("Mariana Duarte", "Que dia será esse evento?"), jwt);
        assertTrue(raiz.respostas().isEmpty());

        // Administração responde (lado admin: autor vem do corpo)
        ComentarioResponse respAdmin = controller
                .responderComentario(raiz.id(), new ComentarRequest("Administração", "Será dia 10"), adminJwt()).getBody();
        assertNotNull(respAdmin);
        assertEquals("Administração", respAdmin.autor());

        // Paciente responde no mesmo comentário-raiz
        feedController.responder(id, raiz.id(), new ComentarRequest("João", "Também quero saber"), jwt);

        // Responder a uma resposta continua ancorado na raiz (threading de 1 nível)
        feedController.responder(id, respAdmin.id(), new ComentarRequest("Mariana Duarte", "Qual horário?"), jwt);

        // Listagem: 1 comentário-raiz com 3 respostas
        Pagina<ComentarioResponse> pagina = feedController.comentarios(id, 0, 20, jwt);
        assertEquals(1, pagina.totalElements());
        assertEquals(3, pagina.content().get(0).respostas().size());

        // Total de comentários (raiz + respostas)
        FeedResponse detalhe = feedController.postagem(id, "dev-x");
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
                () -> feedController.comentar(id, new ComentarRequest("Ana", "oi"), jwt));
        controller.excluir(id);
    }

    @Test
    void editarProprioComentarioDentroDaJanela() {
        Long id = controller.criar(new PostagemRequest("Dica", "Beba água", true, true, 1L, IMG)).id();
        ComentarioResponse c = feedController.comentar(id, new ComentarRequest("João", "otimo"), jwt);
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
        ComentarioResponse c = feedController.comentar(id, new ComentarRequest("João", "antigo"), jwt);
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
        ComentarioResponse raiz = feedController.comentar(id, new ComentarRequest("João", "que horas?"), jwt);
        feedController.responder(id, raiz.id(), new ComentarRequest("João", "eu também"), jwt);
        controller.responderComentario(raiz.id(), new ComentarRequest("Administração", "às 9h"), adminJwt()); // resposta de outro
        assertEquals(3, feedController.postagem(id, "dev-x").totalComentarios());

        // O dono exclui o raiz → apaga o raiz e TODAS as respostas (inclusive a do admin).
        feedController.excluir(id, raiz.id(), jwt);
        assertEquals(0, feedController.comentarios(id, 0, 20, jwt).totalElements());
        assertEquals(0, feedController.postagem(id, "dev-x").totalComentarios());

        controller.excluir(id);
    }

    @Test
    void soODonoPodeEditarOuExcluir() {
        String tel2 = "11933332222";
        pacienteRepository.findByTelefone(tel2).ifPresent(p -> pacienteRepository.deleteById(p.getId()));
        Long outroId = pacienteController.criar(new PacienteRequest("Maria Outra", tel2)).getId();
        String cod2 = pacienteController.gerarCodigo(outroId).getBody().codigo();
        Jwt jwtOutro = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(tel2, cod2, "dev-outro")).token());

        Long id = controller.criar(new PostagemRequest("Regras", "teste", true, true, 1L, IMG)).id();
        ComentarioResponse c = feedController.comentar(id, new ComentarRequest("João", "meu comentário"), jwt);

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
        ComentarioResponse raiz = feedController.comentar(id, new ComentarRequest("João", "dúvida"), jwt);
        ComentarioResponse resp = controller
                .responderComentario(raiz.id(), new ComentarRequest("Administração", "resposta"), adminJwt()).getBody();
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
        feedController.comentar(id, new ComentarRequest("João", "primeiro!"), jwt);
        assertTrue(novoNaLista(id), "comentário de paciente marca a postagem como nova");
        // Filtro "com novos" traz; "sem novos" não traz.
        assertTrue(controller.listar(null, null, null, true, 0, 100).content().stream().anyMatch(p -> p.id().equals(id)));
        assertFalse(controller.listar(null, null, null, false, 0, 100).content().stream().anyMatch(p -> p.id().equals(id)));

        // Admin abre a postagem (buscar lista os comentários) → zera o status.
        assertEquals(200, controller.buscar(id).getStatusCode().value());
        assertFalse(novoNaLista(id), "abrir a postagem zera o status");

        // Resposta do próprio admin NÃO remarca; resposta de paciente remarca.
        var raiz = feedController.comentarios(id, 0, 20, jwt).content().get(0);
        controller.responderComentario(raiz.id(), new ComentarRequest("Administração", "obrigado"), adminJwt());
        assertFalse(novoNaLista(id), "resposta do admin não marca como novo");
        feedController.responder(id, raiz.id(), new ComentarRequest("João", "de nada"), jwt);
        assertTrue(novoNaLista(id), "resposta de paciente marca como novo");

        controller.excluir(id);
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
