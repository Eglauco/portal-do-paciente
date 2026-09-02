package com.example.pop.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.unidade.UnidadeRepository;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

@SpringBootTest
class ChatControllerTest {

    @Autowired
    private ChatController controller;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MensagemRepository mensagemRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private UnidadeRepository unidadeRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    private Long unidadeQualquer() {
        return unidadeRepository.findAll().get(0).getId();
    }

    /** Cria um usuário (atendente) real — responsavel_id/usuario_id têm FK para `usuario`. */
    private Usuario usuarioSalvo(String nome) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail("atendente-" + java.util.UUID.randomUUID() + "@teste.com");
        return usuarioRepository.save(u);
    }

    /** Token de admin (atendente) com o uid + nome do usuário. O nome vem da relação; o claim é só auditoria. */
    private Jwt adminJwt(long uid, String nome) {
        return Jwt.withTokenValue("t").header("alg", "none").claim("uid", uid).claim("nome", nome).build();
    }

    /** Atalho: cria o usuário e devolve o token com o id real dele. */
    private Jwt adminJwt(Usuario u) {
        return adminJwt(u.getId(), u.getNome());
    }

    /** Cria um paciente novo (nome único) com a sessão do app no estado indicado. */
    private Paciente salvarPaciente(boolean ativo, String dispositivoAtivo) {
        Paciente p = new Paciente();
        p.setNome("Teste " + java.util.UUID.randomUUID());
        p.setAtivo(ativo);
        p.setDispositivoAtivo(dispositivoAtivo);
        return pacienteRepository.save(p);
    }

    /** Conversa nova isolada, com um paciente que está usando o app. */
    private Long novaConversaComAppAtivo() {
        Long unidadeId = unidadeQualquer();
        Long pacienteId = salvarPaciente(true, "dev-" + java.util.UUID.randomUUID()).getId();
        return controller.abrir(new AbrirConversaRequest(pacienteId, unidadeId)).getBody().id();
    }

    @Test
    void listaChatsSemeados() {
        Pagina<ChatResponse> pagina = controller.listar(null, null, null, null, false, 0, 50);
        assertTrue(pagina.totalElements() >= 8, "esperado ao menos os chats semeados");
    }

    @Test
    void filtraPorStatusNaoLida() {
        // Garante o cenário: paciente envia e o chat fica NAO_LIDA (independe do seed).
        controller.enviarComoPaciente(2L, new MensagemRequest("Tenho uma dúvida sobre o resultado.", null));

        Pagina<ChatResponse> pagina = controller.listar(null, null, null, StatusChat.NAO_LIDA, false, 0, 50);
        assertTrue(pagina.totalElements() >= 1);
        assertTrue(pagina.content().stream().allMatch(c -> c.status() == StatusChat.NAO_LIDA));
        assertTrue(pagina.content().stream().anyMatch(c -> c.naoLidas() > 0));
    }

    @Test
    void naoResolvidasNaoTrazResolvidos() {
        Pagina<ChatResponse> pagina = controller.listar(null, null, null, null, true, 0, 50);
        assertFalse(pagina.content().isEmpty());
        assertTrue(pagina.content().stream().noneMatch(c -> c.status() == StatusChat.RESOLVIDO));
    }

    @Test
    void detalheTrazMensagensOrdenadas() {
        ChatDetalheResponse detalhe = controller.buscar(1L).getBody();
        assertNotNull(detalhe);
        // >= 3: o chat semeado é compartilhado com o app em dev, então pode ter
        // mensagens extras de uso real; o que importa é a ordem (mais antiga primeiro,
        // do paciente) e trazer ao menos as mensagens semeadas.
        assertTrue(detalhe.mensagens().size() >= 3, "esperado ao menos as mensagens semeadas");
        assertEquals(RemetenteMensagem.PACIENTE, detalhe.mensagens().get(0).remetente());
    }

    @Test
    void marcarEntregueMarcaMensagensDaUnidade() {
        // Conversa nova com paciente usando o app (a unidade só pode enviar nesse caso).
        Long chatId = novaConversaComAppAtivo();
        Jwt jwt = adminJwt(usuarioSalvo("Atendente A"));
        controller.assumir(chatId, jwt);
        controller.enviar(chatId, new MensagemRequest("Resposta da unidade", null), jwt);

        chatService.marcarEntregue(chatId);

        boolean todasEntregues = mensagemRepository.findByChatIdOrderByEnviadaEmAsc(chatId).stream()
                .filter(m -> m.getRemetente() == RemetenteMensagem.UNIDADE)
                .allMatch(Mensagem::isEntregue);
        assertTrue(todasEntregues, "todas as mensagens da unidade deveriam ficar entregues");
    }

    @Test
    void enviarComoUnidadeBloqueiaQuandoPacienteNaoUsaMaisApp() {
        Long unidadeId = unidadeQualquer();
        Long pacienteId = salvarPaciente(true, "dev-" + java.util.UUID.randomUUID()).getId();
        Long chatId = controller.abrir(new AbrirConversaRequest(pacienteId, unidadeId)).getBody().id();
        Jwt jwt = adminJwt(usuarioSalvo("Atendente A"));
        controller.assumir(chatId, jwt);

        // O paciente deixa de usar o app (sessão revogada / trocou de aparelho).
        Paciente p = pacienteRepository.findById(pacienteId).orElseThrow();
        p.setDispositivoAtivo(null);
        pacienteRepository.save(p);

        // A unidade não consegue mais enviar (422)...
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.enviar(chatId, new MensagemRequest("Você está aí?", null), jwt));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());

        // ...e o detalhe informa o bloqueio para a tela mostrar o aviso fixo.
        ChatDetalheResponse detalhe = controller.buscar(chatId).getBody();
        assertNotNull(detalhe);
        assertFalse(detalhe.pacienteUsandoApp());
    }

    @Test
    void reenvioComMesmoClienteIdNaoDuplica() {
        String clienteId = java.util.UUID.randomUUID().toString();
        int antes = mensagemRepository.findByChatIdOrderByEnviadaEmAsc(2L).size();

        controller.enviarComoPaciente(2L, new MensagemRequest("mensagem idempotente", clienteId));
        controller.enviarComoPaciente(2L, new MensagemRequest("mensagem idempotente", clienteId)); // reenvio

        int depois = mensagemRepository.findByChatIdOrderByEnviadaEmAsc(2L).size();
        assertEquals(antes + 1, depois, "reenvio com o mesmo clienteId nao deve duplicar");
    }

    @Test
    void abrirCriaEReutilizaConversaDoPacienteQueUsaApp() {
        Long unidadeId = unidadeQualquer();
        Long pacienteId = salvarPaciente(true, "dev-" + java.util.UUID.randomUUID()).getId();

        // 1ª vez: cria (201).
        ResponseEntity<ChatDetalheResponse> criacao = controller.abrir(new AbrirConversaRequest(pacienteId, unidadeId));
        assertEquals(HttpStatus.CREATED, criacao.getStatusCode());
        ChatDetalheResponse criado = criacao.getBody();
        assertNotNull(criado);
        assertNotNull(criado.id());
        assertEquals(pacienteId, criado.paciente().id());
        assertTrue(criado.mensagens().isEmpty(), "conversa nova começa sem mensagens");
        assertTrue(criado.pacienteUsandoApp(), "paciente com sessão ativa está usando o app");

        // 2ª vez: reutiliza a mesma (200), sem duplicar (1 por paciente+unidade).
        ResponseEntity<ChatDetalheResponse> reabertura = controller.abrir(new AbrirConversaRequest(pacienteId, unidadeId));
        assertEquals(HttpStatus.OK, reabertura.getStatusCode());
        assertNotNull(reabertura.getBody());
        assertEquals(criado.id(), reabertura.getBody().id());
    }

    @Test
    void abrirBloqueiaPacienteQueNaoEstaUsandoOApp() {
        Long unidadeId = unidadeQualquer();
        // ativo=true mas sem aparelho amarrado (só recebeu o código, não ativou no celular).
        Long pacienteId = salvarPaciente(true, null).getId();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.abrir(new AbrirConversaRequest(pacienteId, unidadeId)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void pacienteEnviaMensagemEChatFicaNaoLida() {
        ChatDetalheResponse antes = controller.buscar(4L).getBody();
        assertNotNull(antes);
        int qtdAntes = antes.mensagens().size();

        ChatDetalheResponse depois = controller
                .enviarComoPaciente(4L, new MensagemRequest("Olá, tenho uma dúvida sobre o exame.", null)).getBody();
        assertNotNull(depois);
        assertEquals(qtdAntes + 1, depois.mensagens().size());

        MensagemResponse ultima = depois.mensagens().get(depois.mensagens().size() - 1);
        assertEquals(RemetenteMensagem.PACIENTE, ultima.remetente());
        assertFalse(ultima.lida());
        assertEquals(StatusChat.NAO_LIDA, depois.status());
    }

    @Test
    void enviarSemAssumirBloqueia() {
        Long chatId = novaConversaComAppAtivo();
        Jwt jwt = adminJwt(usuarioSalvo("Atendente A"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.enviar(chatId, new MensagemRequest("oi", null), jwt));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void assumirRegistraResponsavelEAtribuiMensagem() {
        Long chatId = novaConversaComAppAtivo();
        Usuario atendente = usuarioSalvo("Atendente A");
        Jwt a = adminJwt(atendente);
        ChatDetalheResponse aposAssumir = controller.assumir(chatId, a).getBody();
        assertNotNull(aposAssumir);
        assertEquals(atendente.getId(), aposAssumir.responsavelId());
        assertEquals("Atendente A", aposAssumir.responsavelNome());

        ChatDetalheResponse aposEnviar = controller.enviar(chatId, new MensagemRequest("Olá!", null), a).getBody();
        MensagemResponse ultima = aposEnviar.mensagens().get(aposEnviar.mensagens().size() - 1);
        assertEquals(RemetenteMensagem.UNIDADE, ultima.remetente());
        assertEquals("Atendente A", ultima.atendenteNome());
    }

    @Test
    void outroAtendenteSoEnviaAposTransferir() {
        Long chatId = novaConversaComAppAtivo();
        Jwt a = adminJwt(usuarioSalvo("Atendente A"));
        Jwt b = adminJwt(usuarioSalvo("Atendente B"));
        controller.assumir(chatId, a);

        // B não é responsável: 409 citando o responsável atual.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.enviar(chatId, new MensagemRequest("posso responder?", null), b));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Atendente A"));

        // B transfere para si e passa a enviar; A não consegue mais.
        controller.assumir(chatId, b);
        controller.enviar(chatId, new MensagemRequest("assumido", null), b);
        ResponseStatusException exA = assertThrows(ResponseStatusException.class,
                () -> controller.enviar(chatId, new MensagemRequest("voltei", null), a));
        assertEquals(HttpStatus.CONFLICT, exA.getStatusCode());
        assertTrue(exA.getReason().contains("Atendente B"));
    }

    @Test
    void filtraPorResponsavel() {
        Long chatId = novaConversaComAppAtivo();
        Usuario resp = usuarioSalvo("Atendente Filtro");
        controller.assumir(chatId, adminJwt(resp));

        // Filtrando pelo responsável: a conversa aparece e todas são desse responsável.
        Pagina<ChatResponse> doResponsavel = controller.listar(null, null, resp.getId(), null, false, 0, 50);
        assertTrue(doResponsavel.content().stream().anyMatch(c -> c.id().equals(chatId)));
        assertTrue(doResponsavel.content().stream()
                .allMatch(c -> c.responsavelId() != null && c.responsavelId().equals(resp.getId())));

        // Filtrando por outro responsável: a conversa não aparece.
        Pagina<ChatResponse> deOutro = controller.listar(null, null, resp.getId() + 999_999, null, false, 0, 50);
        assertTrue(deOutro.content().stream().noneMatch(c -> c.id().equals(chatId)));
    }
}
