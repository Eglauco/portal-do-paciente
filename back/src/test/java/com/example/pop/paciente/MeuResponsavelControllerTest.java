package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.verificacao.VerificacaoService;

/** O paciente adicionar/gerenciar responsáveis pelo app, com escopo travado em AGENDAMENTOS. */
@SpringBootTest
class MeuResponsavelControllerTest {

    private static final String TEL = "11955557777";

    @Autowired
    private MeuResponsavelController controller;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private ResponsavelRepository responsavelRepository;
    @Autowired
    private PacienteLogService pacienteLogService;
    @Autowired
    private PacienteLogRepository pacienteLogRepository;
    @Autowired
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private VerificacaoService verificacao;

    private Long pacienteId;
    /** Token da sessão do PRÓPRIO paciente (telefone = do paciente). */
    private Jwt jwt;

    @BeforeEach
    void setup() {
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> pacienteRepository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Ana Titular", TEL), null).getId();
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        String token = authController.ativar(new AtivarPacienteRequest(TEL, "000000", "dev-resp")).token();
        jwt = jwtDecoder.decode(token);
    }

    @AfterEach
    void limpar() {
        pacienteRepository.deleteById(pacienteId);
    }

    @Test
    void adicionaComEscopoDeAgendamentoListaERemove() {
        MeuResponsavelResponse criado = controller
                .adicionar(jwt, new MeuResponsavelRequest("Maria Ajuda", "(11) 98888-1111")).getBody();
        assertNotNull(criado);
        assertEquals("11988881111", criado.telefone(), "telefone normalizado (só dígitos)");

        // Escopo TRAVADO no servidor: só AGENDAMENTOS = VISUALIZAR_LANCAR, e origem PACIENTE.
        Responsavel r = responsavelRepository.findById(criado.id()).orElseThrow();
        assertEquals(OrigemResponsavel.PACIENTE, r.getOrigem());
        assertEquals(1, r.getPermissoes().size(), "somente uma funcionalidade liberada");
        assertEquals(NivelAcessoResponsavel.VISUALIZAR_LANCAR, r.getPermissoes().get(FuncionalidadeApp.AGENDAMENTOS));

        assertTrue(controller.listar(jwt).stream().anyMatch(x -> x.id().equals(criado.id())));

        // Auditoria (LGPD): a adição pelo app vira um evento de ALTERAÇÃO com autor PACIENTE
        // (além do log de CRIAÇÃO do cadastro, feito no setup — por isso filtramos por PACIENTE).
        var logsAdd = pacienteLogService.listar(pacienteId).stream()
                .filter(l -> l.autor() == AutorLogPaciente.PACIENTE).toList();
        assertEquals(1, logsAdd.size(), "um evento de auditoria do app após adicionar");
        assertEquals(TipoEventoPaciente.ALTERACAO, logsAdd.get(0).tipo());
        assertEquals("Ana Titular", logsAdd.get(0).autorNome(), "ator = o próprio paciente");
        assertTrue(logsAdd.get(0).alteracoes().stream().anyMatch(
                a -> "RESPONSAVEL".equals(a.campo()) && a.valorAntes() == null
                        && a.valorDepois() != null && a.valorDepois().contains("Maria Ajuda")),
                "linha 'Responsável adicionado' com os detalhes");

        // Dedup: mesmo telefone → 409.
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> controller.adicionar(jwt, new MeuResponsavelRequest("Outro", "11988881111")))
                .getStatusCode().value());

        // Sem lançamentos → remove de fato; some da lista.
        controller.remover(jwt, criado.id());
        assertTrue(controller.listar(jwt).isEmpty());

        // Auditoria: a remoção pelo app gera um segundo evento do app (também autor PACIENTE).
        var logsApp = pacienteLogService.listar(pacienteId).stream()
                .filter(l -> l.autor() == AutorLogPaciente.PACIENTE).toList();
        assertEquals(2, logsApp.size(), "adição + remoção auditadas pelo app");
        assertTrue(logsApp.get(1).alteracoes().stream().anyMatch(
                a -> "RESPONSAVEL".equals(a.campo()) && a.valorDepois() == null
                        && a.valorAntes() != null && a.valorAntes().contains("Maria Ajuda")),
                "linha 'Responsável removido' com os detalhes");
    }

    @Test
    void comLancamentosBloqueiaExclusaoEInativaReativaComAuditoria() {
        MeuResponsavelResponse criado = controller
                .adicionar(jwt, new MeuResponsavelRequest("Bia Ajuda", "(11) 97777-2222")).getBody();
        assertNotNull(criado);
        assertTrue(criado.ativo());
        assertTrue(criado.podeExcluir(), "recém-criado, sem lançamentos → pode excluir");
        Responsavel r = responsavelRepository.findById(criado.id()).orElseThrow();

        // Semeia um "lançamento" do responsável (evento de auditoria com ele como ATOR) para
        // temLancamentos() ser true → a partir daqui só cabe inativar/reativar, não excluir.
        PacienteLog marca = new PacienteLog();
        marca.setPaciente(pacienteRepository.findById(pacienteId).orElseThrow());
        marca.setResponsavel(r);
        marca.setTipo(TipoEventoPaciente.ALTERACAO);
        marca.setAutor(AutorLogPaciente.RESPONSAVEL);
        marca.setCriadoEm(LocalDateTime.now());
        pacienteLogRepository.save(marca);

        // Com lançamentos: EXCLUIR é bloqueado (409) — mesma regra do back-office.
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> controller.remover(jwt, criado.id())).getStatusCode().value());

        // Inativar (PATCH ativo=false): audita a mudança de situação; podeExcluir=false.
        MeuResponsavelResponse inativo = controller.definirSituacao(jwt, criado.id(),
                new MeuResponsavelSituacaoRequest(false));
        assertFalse(inativo.ativo());
        assertFalse(inativo.podeExcluir(), "com lançamentos, não pode excluir");
        assertEquals(1, contarLinhasDeSituacaoPeloApp(), "uma linha 'Situação: ativo → inativo'");

        // O inativo CONTINUA na lista (para poder reativar).
        assertTrue(controller.listar(jwt).stream().anyMatch(x -> x.id().equals(criado.id()) && !x.ativo()));

        // Idempotência: inativar de novo NÃO grava outra linha.
        controller.definirSituacao(jwt, criado.id(), new MeuResponsavelSituacaoRequest(false));
        assertEquals(1, contarLinhasDeSituacaoPeloApp(), "reinativar não gera auditoria falsa");

        // Reativar (PATCH ativo=true): audita a volta.
        MeuResponsavelResponse reativo = controller.definirSituacao(jwt, criado.id(),
                new MeuResponsavelSituacaoRequest(true));
        assertTrue(reativo.ativo());
        assertEquals(2, contarLinhasDeSituacaoPeloApp(), "inativar + reativar = duas linhas de situação");
    }

    /** Conta as linhas de mudança de 'Situação' em eventos do app (autor PACIENTE). */
    private long contarLinhasDeSituacaoPeloApp() {
        return pacienteLogService.listar(pacienteId).stream()
                .filter(l -> l.autor() == AutorLogPaciente.PACIENTE)
                .flatMap(l -> l.alteracoes().stream())
                .filter(a -> a.campoDescricao() != null && a.campoDescricao().contains("Situação"))
                .count();
    }

    @Test
    void naoPermiteAdicionarOProprioTelefone() {
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> controller.adicionar(jwt, new MeuResponsavelRequest("Eu mesmo", TEL)))
                .getStatusCode().value());
    }

    @Test
    void telefoneInvalidoEhRejeitado() {
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> controller.adicionar(jwt, new MeuResponsavelRequest("Curto", "123")))
                .getStatusCode().value());
    }
}
