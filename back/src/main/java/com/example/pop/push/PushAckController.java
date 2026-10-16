package com.example.pop.push;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.chat.ChatService;
import com.example.pop.inquilino.InquilinoService;
import com.example.pop.tenant.TenantContext;

/**
 * ACK de ENTREGA por push (2º "check" com o app do paciente FECHADO).
 *
 * <p>A Notification Service Extension do iOS e a background task do Android chamam este
 * endpoint PÚBLICO ecoando o token assinado que veio no push ({@code data.ack}) — sem Bearer,
 * porque esses contextos não têm acesso fácil à sessão do paciente. O token (HMAC) carrega o
 * inquilino + paciente; como não há JWT, resolvemos o schema a partir dele (salvando/restaurando
 * o contexto, como os webhooks de assinatura), marcamos as mensagens da unidade como entregues e
 * limpamos o contexto. A ação é idempotente ({@link ChatService#marcarEntregueTodasDoPaciente}).
 */
@RestController
public class PushAckController {

    private final PushAckToken pushAckToken;
    private final InquilinoService inquilinoService;
    private final ChatService chatService;

    public PushAckController(PushAckToken pushAckToken, InquilinoService inquilinoService, ChatService chatService) {
        this.pushAckToken = pushAckToken;
        this.inquilinoService = inquilinoService;
        this.chatService = chatService;
    }

    /** Corpo: o token opaco que veio no push ({@code data.ack}). */
    public record EntregaPushRequest(String ack) {
    }

    @PostMapping("/push/entregue")
    public void entregue(@RequestBody(required = false) EntregaPushRequest request) {
        PushAckToken.Conteudo conteudo = pushAckToken.verificar(request == null ? null : request.ack());
        if (conteudo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token de entrega inválido ou expirado");
        }
        // Sem JWT aqui: o TenantFilter não rodou. Fixa o schema pelo inquilino do token (verificado)
        // ANTES de abrir a transação do serviço, e restaura no finally (padrão dos webhooks).
        String anterior = TenantContext.atualBruto();
        try {
            TenantContext.definir(inquilinoService.schemaPorId(conteudo.inquilinoId()));
            chatService.marcarEntregueTodasDoPaciente(conteudo.pacienteId());
        } finally {
            if (anterior != null) {
                TenantContext.definir(anterior);
            } else {
                TenantContext.limpar();
            }
        }
    }
}
