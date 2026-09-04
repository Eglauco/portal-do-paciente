package com.example.pop.notificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.verificacao.VerificacaoService;

@SpringBootTest
class MeuNotificacaoControllerTest {

    private static final String TEL = "11955558888";

    @Autowired
    private MeuNotificacaoController controller;
    @Autowired
    private NotificacaoService notificacaoService;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private VerificacaoService verificacao;

    private Long pacienteId;
    private Jwt jwt;

    @BeforeEach
    void setup() {
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> {
            notificacaoRepository.deleteAll(
                    notificacaoRepository.findByPacienteIdOrderByCriadoEmDesc(p.getId(),
                            org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
            pacienteRepository.deleteById(p.getId());
        });
        pacienteId = pacienteController.criar(new PacienteRequest("Notif Paciente", TEL)).getId();
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        jwt = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-notif")).token());
    }

    @AfterEach
    void limpar() {
        notificacaoRepository.deleteAll(
                notificacaoRepository.findByPacienteIdOrderByCriadoEmDesc(pacienteId,
                        org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
        pacienteRepository.deleteById(pacienteId);
    }

    @Test
    void listaContaEMarcaLida() {
        // Grava três notificações (como o backend faz ao disparar push).
        notificacaoService.registrar(pacienteId, TipoNotificacao.AGENDAMENTO, "Novo agendamento", "corpo 1", 10L);
        notificacaoService.registrar(pacienteId, TipoNotificacao.NPS, "Avalie seu atendimento", "corpo 2", null);
        notificacaoService.registrar(pacienteId, TipoNotificacao.SAU, "Resposta do SAU", "corpo 3", 20L);

        // Lista: 3 itens, mais recentes primeiro, todas não lidas.
        List<NotificacaoResponse> itens = controller.listar(jwt, 0, 50).content();
        assertEquals(3, itens.size());
        assertEquals(TipoNotificacao.SAU, itens.get(0).tipo());
        assertTrue(itens.stream().noneMatch(NotificacaoResponse::lida));

        // Contador do sino: 3 não lidas.
        assertEquals(3L, controller.naoLidas(jwt).total());

        // Marca a primeira como lida → contador cai para 2 e o item vem "lida".
        Long alvo = itens.get(0).id();
        controller.marcarLida(jwt, alvo);
        assertEquals(2L, controller.naoLidas(jwt).total());
        NotificacaoResponse relida = controller.listar(jwt, 0, 50).content().stream()
                .filter(n -> n.id().equals(alvo)).findFirst().orElseThrow();
        assertTrue(relida.lida());
    }

    @Test
    void marcarTodasComoLidasZeraContador() {
        notificacaoService.registrar(pacienteId, TipoNotificacao.AGENDAMENTO, "Novo agendamento", "corpo 1", 10L);
        notificacaoService.registrar(pacienteId, TipoNotificacao.NPS, "Avalie seu atendimento", "corpo 2", null);
        notificacaoService.registrar(pacienteId, TipoNotificacao.SAU, "Resposta do SAU", "corpo 3", 20L);
        assertEquals(3L, controller.naoLidas(jwt).total());

        controller.marcarTodasLidas(jwt);

        assertEquals(0L, controller.naoLidas(jwt).total());
        assertTrue(controller.listar(jwt, 0, 50).content().stream().allMatch(NotificacaoResponse::lida));
    }

    @Test
    void naoEnxergaNemMarcaNotificacaoDeOutroPaciente() {
        // Notificação de OUTRO paciente não deve aparecer nem ser marcável por este.
        Long outroId = pacienteController.criar(new PacienteRequest("Outro Notif", "11955557777")).getId();
        try {
            notificacaoService.registrar(outroId, TipoNotificacao.FALTA, "Falta registrada", "corpo", 30L);
            Long idOutro = notificacaoRepository.findByPacienteIdOrderByCriadoEmDesc(outroId,
                    org.springframework.data.domain.PageRequest.of(0, 10)).getContent().get(0).getId();

            assertTrue(controller.listar(jwt, 0, 50).content().isEmpty());
            assertEquals(0L, controller.naoLidas(jwt).total());

            // marcarLida com id de outro paciente é no-op (escopo por paciente) — segue não lida.
            controller.marcarLida(jwt, idOutro);
            assertFalse(notificacaoRepository.findById(idOutro).orElseThrow().isLida());
        } finally {
            notificacaoRepository.deleteAll(
                    notificacaoRepository.findByPacienteIdOrderByCriadoEmDesc(outroId,
                            org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
            pacienteRepository.deleteById(outroId);
        }
    }
}
