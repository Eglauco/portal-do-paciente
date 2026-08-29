package com.example.pop.postagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private JwtDecoder jwtDecoder;

    private Long pacienteId;
    /** Token do paciente logado — comentar/responder derivam o autor dele. */
    private Jwt jwt;

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
        assertEquals(1, feedController.comentarios(id, 0, 20).totalElements());

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
                .responderComentario(raiz.id(), new ComentarRequest("Administração", "Será dia 10")).getBody();
        assertNotNull(respAdmin);
        assertEquals("Administração", respAdmin.autor());

        // Paciente responde no mesmo comentário-raiz
        feedController.responder(id, raiz.id(), new ComentarRequest("João", "Também quero saber"), jwt);

        // Responder a uma resposta continua ancorado na raiz (threading de 1 nível)
        feedController.responder(id, respAdmin.id(), new ComentarRequest("Mariana Duarte", "Qual horário?"), jwt);

        // Listagem: 1 comentário-raiz com 3 respostas
        Pagina<ComentarioResponse> pagina = feedController.comentarios(id, 0, 20);
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
    void filtraPorTitulo() {
        PostagemDetalheResponse criada = controller.criar(new PostagemRequest(
                "Semana da Saúde 2026", "Programação especial", false, true, 1L, IMG));
        Pagina<PostagemResponse> pagina = controller.listar("Semana da Saúde", null, null, 0, 10);
        assertTrue(pagina.content().stream().anyMatch(p -> p.id().equals(criada.id())));
        controller.excluir(criada.id());
    }
}
