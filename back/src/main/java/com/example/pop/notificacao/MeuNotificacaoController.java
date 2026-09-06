package com.example.pop.notificacao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.pop.common.Pagina;
import com.example.pop.paciente.FuncionalidadeApp;
import com.example.pop.paciente.NivelAcessoResponsavel;
import com.example.pop.paciente.PacienteAcessoService;
import com.example.pop.paciente.Responsavel;

/** Notificações do paciente logado (app). Sob /meu/** → papel PACIENTE. */
@RestController
@RequestMapping("/meu/notificacoes")
public class MeuNotificacaoController {

    private final NotificacaoService service;
    private final PacienteAcessoService acessoService;

    public MeuNotificacaoController(NotificacaoService service, PacienteAcessoService acessoService) {
        this.service = service;
        this.acessoService = acessoService;
    }

    /** Lista as notificações do paciente (mais recentes primeiro), sem os tipos bloqueados por permissão. */
    @GetMapping
    public Pagina<NotificacaoResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return service.listar(pacienteId, tiposBloqueados(jwt), page, size);
    }

    /** Total de notificações ainda não lidas (contador do sino), sem os tipos bloqueados. */
    @GetMapping("/nao-lidas")
    public ContagemNaoLidas naoLidas(@AuthenticationPrincipal Jwt jwt) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        return new ContagemNaoLidas(service.contarNaoLidas(pacienteId, tiposBloqueados(jwt)));
    }

    /**
     * Tipos de notificação que a sessão NÃO pode ver por causa da permissão do responsável:
     * um tipo é bloqueado quando a funcionalidade que ele representa está SEM_ACESSO. Perfil
     * próprio (sem responsável na sessão) não bloqueia nada. NPS e PRONTUÁRIO ficam sempre
     * visíveis (fora da trava). Igual à supressão do push, agora também no inbox persistido.
     */
    private List<TipoNotificacao> tiposBloqueados(Jwt jwt) {
        Responsavel responsavel = acessoService.responsavelDaSessao(jwt).orElse(null);
        if (responsavel == null) {
            return List.of(); // perfil próprio: vê tudo
        }
        Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes = responsavel.getPermissoes();
        List<TipoNotificacao> bloqueados = new ArrayList<>();
        for (TipoNotificacao tipo : TipoNotificacao.values()) {
            FuncionalidadeApp funcionalidade = funcionalidadeDe(tipo);
            if (funcionalidade != null
                    && permissoes.getOrDefault(funcionalidade, NivelAcessoResponsavel.SEM_ACESSO)
                            == NivelAcessoResponsavel.SEM_ACESSO) {
                bloqueados.add(tipo);
            }
        }
        return bloqueados;
    }

    /** Funcionalidade controlada por trás de cada tipo de notificação (null = sempre visível). */
    private static FuncionalidadeApp funcionalidadeDe(TipoNotificacao tipo) {
        return switch (tipo) {
            case AGENDAMENTO, FALTA, LEMBRETE -> FuncionalidadeApp.AGENDAMENTOS;
            case SAU -> FuncionalidadeApp.SAU;
            case POSTAGEM -> FuncionalidadeApp.REDE_SOCIAL;
            case PRONTUARIO -> FuncionalidadeApp.PRONTUARIO;
            case NPS -> FuncionalidadeApp.NPS;
        };
    }

    /** Marca a notificação como lida (ao tocar nela no app). */
    @PostMapping("/{id}/lida")
    public void marcarLida(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        service.marcarLida(id, pacienteId);
    }

    /** Marca TODAS as notificações do paciente como lidas (botão "marcar todas como lidas"). */
    @PostMapping("/marcar-todas-lidas")
    public void marcarTodasLidas(@AuthenticationPrincipal Jwt jwt) {
        Long pacienteId = acessoService.pacienteDoToken(jwt).getId();
        service.marcarTodasLidas(pacienteId);
    }

    public record ContagemNaoLidas(long total) {
    }
}
