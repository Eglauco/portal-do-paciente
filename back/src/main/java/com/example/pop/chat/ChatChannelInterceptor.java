package com.example.pop.chat;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.NivelAcessoResponsavel;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteAcessoService;

/**
 * Segurança do chat em tempo real (STOMP): autentica o CONNECT pelo token (Bearer
 * no header do frame) e autoriza SUBSCRIBE/SEND por posse — um paciente só
 * assina/escreve na conversa dele; o admin (back-office) enxerga todas. Fecha o
 * vazamento de mensagens em tempo real via /topic/chat/{id}.
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    /** Casa /topic/chat/{id}, /topic/chat/{id}/digitando e /app/chat/{id}/digitando. */
    private static final Pattern DESTINO_CHAT = Pattern.compile("^/(?:topic|app)/chat/(\\d+)(?:/.*)?$");

    private final JwtDecoder jwtDecoder;
    private final PacienteAcessoService acessoService;
    private final ChatRepository chatRepository;

    public ChatChannelInterceptor(JwtDecoder jwtDecoder, PacienteAcessoService acessoService,
            ChatRepository chatRepository) {
        this.jwtDecoder = jwtDecoder;
        this.acessoService = acessoService;
        this.chatRepository = chatRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand comando = accessor.getCommand();
        if (StompCommand.CONNECT.equals(comando)) {
            accessor.setUser(autenticar(accessor));
        } else if (StompCommand.SUBSCRIBE.equals(comando) || StompCommand.SEND.equals(comando)) {
            autorizar(accessor);
        }
        return message;
    }

    /** Valida o token do frame CONNECT e devolve o principal (paciente ou admin). */
    private Principal autenticar(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Chat: token ausente");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring(7).trim());
        } catch (RuntimeException e) {
            throw new MessagingException("Chat: token inválido");
        }
        String papel = jwt.getClaimAsString("role");
        if ("PACIENTE".equals(papel)) {
            Paciente paciente = acessoService.pacienteDoToken(jwt); // valida a conta (própria ou dependente) + aparelho
            Object cid = jwt.getClaim("cid");
            Long contaId = cid instanceof Number numero ? numero.longValue() : null;
            return new ChatPrincipal("PACIENTE:" + paciente.getId(), "PACIENTE", paciente.getId(),
                    contaId, jwt.getClaimAsString("dev"));
        }
        if ("ADMIN".equals(papel)) {
            Object uid = jwt.getClaim("uid");
            Long id = uid instanceof Number numero ? numero.longValue() : null;
            return new ChatPrincipal("ADMIN:" + id, "ADMIN", id, null, null);
        }
        throw new MessagingException("Chat: papel inválido");
    }

    /** Autoriza SUBSCRIBE/SEND: paciente só na própria conversa; admin em todas. */
    private void autorizar(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof ChatPrincipal principal)) {
            throw new MessagingException("Chat: não autenticado");
        }
        String destino = accessor.getDestination();
        // Clientes só publicam em /app/** (ex.: /app/chat/{id}/digitando). SEND para
        // destinos de broker (/topic/**) é proibido — impede forjar mensagem/impersonar
        // a unidade e spam no sinal de lista.
        if (StompCommand.SEND.equals(accessor.getCommand()) && (destino == null || !destino.startsWith("/app/"))) {
            throw new MessagingException("Chat: publicação só é permitida em /app");
        }
        if (destino == null) {
            return;
        }
        // Deny-by-default para CURINGAS. O cliente sempre assina/publica destinos
        // concretos; um padrão STOMP (ex.: /topic/chat/**, /topic/chat/*) NÃO casa a
        // regex numérica e cairia no `return` liberado abaixo — mas o broker
        // (AntPathMatcher) o registraria como PADRÃO e entregaria TODA mensagem de
        // /topic/chat/{id}, furando a checagem de posse (vazamento de conversas ao vivo).
        if (ehPadrao(destino)) {
            throw new MessagingException("Chat: destino com curinga não é permitido");
        }
        Matcher m = DESTINO_CHAT.matcher(destino);
        if (!m.matches()) {
            // Deny-by-default no namespace por conversa: qualquer /topic/chat/... ou
            // /app/chat/... fora do formato numérico estrito é rejeitado (não cai no
            // `return` liberado). /topic/chats (sinal de lista) não casa esse prefixo.
            if (destino.startsWith("/topic/chat/") || destino.startsWith("/app/chat/")) {
                throw new MessagingException("Chat: destino inválido");
            }
            return; // /topic/chats e demais destinos concretos: liberado para autenticados
        }
        if ("ADMIN".equals(principal.role())) {
            return; // back-office enxerga todas as conversas
        }
        // Paciente: revalida a sessão a cada assinatura (respeita revogação/troca de aparelho)
        // e confere a posse da conversa. Perfil próprio ou dependente: a conta (cid) valida
        // ambos; sem cid (token antigo), cai no modelo legado do próprio paciente.
        acessoService.revalidarSessaoPaciente(principal.cid(), principal.id(), principal.dev());
        Long chatId = Long.valueOf(m.group(1));
        Long dono = chatRepository.findPacienteIdById(chatId).orElse(null);
        if (dono == null || !dono.equals(principal.id())) {
            throw new MessagingException("Chat: sem acesso a esta conversa");
        }
        // Trava por funcionalidade: um responsável SEM_ACESSO ao Chat deste perfil não
        // assina o tempo real (perfil próprio/legado passa: nível total).
        if (acessoService.nivelPorContaEPerfil(principal.cid(), principal.id(), FuncionalidadeApp.CHAT)
                == NivelAcessoResponsavel.SEM_ACESSO) {
            throw new MessagingException("Chat: sem acesso a esta funcionalidade");
        }
    }

    /**
     * O destino é um PADRÃO STOMP (curinga) que o broker (AntPathMatcher) casaria
     * contra vários destinos concretos. Espelha {@code AntPathMatcher.isPattern}:
     * {@code *}, {@code ?} ou um template {@code {…}}. Assinaturas/publicações do
     * chat são sempre para destinos concretos, então qualquer padrão é rejeitado.
     */
    private static boolean ehPadrao(String destino) {
        return destino.indexOf('*') >= 0 || destino.indexOf('?') >= 0 || destino.indexOf('{') >= 0;
    }
}
