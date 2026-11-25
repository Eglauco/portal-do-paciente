package com.example.pop.agendamento;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Convite MANUAL do app (back-office): os botões de WhatsApp e SMS ao lado de cada horário (lista da agenda,
 * detalhe do horário e marcações por paciente). Sob {@code /horario/**} — só ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/horario")
public class HorarioConviteController {

    private final HorarioConviteService service;

    public HorarioConviteController(HorarioConviteService service) {
        this.service = service;
    }

    /** Corpo do envio por SMS: os celulares (do cadastro do paciente) escolhidos pelo atendente. */
    public record EnviarSmsRequest(List<String> telefones) {
    }

    /** Corpo do registro do WhatsApp: o celular para o qual o atendente abriu a conversa. */
    public record WhatsappRequest(String telefone) {
    }

    /** Para cada paciente pedido: já tem o app? e os celulares do cadastro. Mais o texto do convite (uma vez). */
    @GetMapping("/convite/opcoes")
    public HorarioConviteService.Opcoes opcoes(@RequestParam List<Long> pacienteIds) {
        return service.opcoes(pacienteIds);
    }

    /** Envia o SMS de convite para os celulares escolhidos e registra no histórico do horário. */
    @PostMapping("/{id}/convite/sms")
    public HorarioConviteService.ResultadoSms enviarSms(@PathVariable Long id, @RequestBody EnviarSmsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.enviarSms(id, request == null ? null : request.telefones(), uidDoToken(jwt));
    }

    /** Registra no histórico do horário que o atendente abriu o WhatsApp com o convite (o envio em si é no WhatsApp). */
    @PostMapping("/{id}/convite/whatsapp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarWhatsapp(@PathVariable Long id, @RequestBody WhatsappRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        service.registrarWhatsapp(id, request == null ? null : request.telefone(), uidDoToken(jwt));
    }

    private static Long uidDoToken(Jwt jwt) {
        return jwt != null && jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
    }
}
