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
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.unidade.UnidadeRepository;

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

    private Long unidadeQualquer() {
        return unidadeRepository.findAll().get(0).getId();
    }

    /** Cria um paciente novo (nome único) com a sessão do app no estado indicado. */
    private Paciente salvarPaciente(boolean ativo, String dispositivoAtivo) {
        Paciente p = new Paciente();
        p.setNome("Teste " + java.util.UUID.randomUUID());
        p.setAtivo(ativo);
        p.setDispositivoAtivo(dispositivoAtivo);
        return pacienteRepository.save(p);
    }

    @Test
    void listaChatsSemeados() {
        Pagina<ChatResponse> pagina = controller.listar(null, null, null, false, 0, 50);
        assertTrue(pagina.totalElements() >= 8, "esperado ao menos os chats semeados");
    }

    @Test
    void filtraPorStatusNaoLida() {
        // Garante o cenário: paciente envia e o chat fica NAO_LIDA (independe do seed).
        controller.enviarComoPaciente(2L, new MensagemRequest("Tenho uma dúvida sobre o resultado.", null));

        Pagina<ChatResponse> pagina = controller.listar(null, null, StatusChat.NAO_LIDA, false, 0, 50);
        assertTrue(pagina.totalElements() >= 1);
        assertTrue(pagina.content().stream().allMatch(c -> c.status() == StatusChat.NAO_LIDA));
        assertTrue(pagina.content().stream().anyMatch(c -> c.naoLidas() > 0));
    }

    @Test
    void naoResolvidasNaoTrazResolvidos() {
        Pagina<ChatResponse> pagina = controller.listar(null, null, null, true, 0, 50);
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
        // Garante ao menos uma mensagem da unidade no chat.
        controller.enviar(1L, new MensagemRequest("Resposta da unidade", null));

        chatService.marcarEntregue(1L);

        boolean todasEntregues = mensagemRepository.findByChatIdOrderByEnviadaEmAsc(1L).stream()
                .filter(m -> m.getRemetente() == RemetenteMensagem.UNIDADE)
                .allMatch(Mensagem::isEntregue);
        assertTrue(todasEntregues, "todas as mensagens da unidade deveriam ficar entregues");
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
}
