package com.example.pop.paciente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

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
    private static final String CPF = "10000000030";
    // CPFs válidos (dígito verificador) para os responsáveis do teste.
    private static final String CPF_RESP_A = "52998224725";
    private static final String CPF_RESP_B = "11144477735";
    private static final java.time.LocalDate DOB = java.time.LocalDate.of(1990, 1, 1);
    private static final java.time.LocalDate DOB_RESP = java.time.LocalDate.of(1980, 5, 10);

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
        pacienteRepository.buscarPorTelefoneNaLista(TEL).forEach(p -> pacienteRepository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Ana Titular", TEL), null).getId();
        pacienteRepository.findById(pacienteId).ifPresent(p -> {
            p.setCpf(CPF);
            p.setDataNascimento(DOB);
            pacienteRepository.save(p);
        });
        when(verificacao.checar(anyString(), anyString())).thenReturn(true);
        String token = authController.ativar(new AtivarPacienteRequest(CPF, DOB, "000000", "dev-resp", TEL)).token();
        jwt = jwtDecoder.decode(token);
    }

    @AfterEach
    void limpar() {
        pacienteRepository.deleteById(pacienteId);
    }

    @Test
    void adicionaComEscopoDeAgendamentoListaERemove() {
        MeuResponsavelResponse criado = controller
                .adicionar(jwt, new MeuResponsavelRequest("Maria Ajuda", CPF_RESP_A, DOB_RESP, "(11) 98888-1111",
                        Map.of(FuncionalidadeApp.AGENDAMENTOS, NivelAcessoResponsavel.VISUALIZAR_LANCAR,
                                FuncionalidadeApp.CHAT, NivelAcessoResponsavel.VISUALIZAR)))
                .getBody();
        assertNotNull(criado);
        assertEquals("11988881111", criado.telefone(), "telefone normalizado (só dígitos)");

        // As permissões escolhidas pelo paciente são aplicadas (origem PACIENTE), sem trava fixa em Agendamentos.
        Responsavel r = responsavelRepository.findById(criado.id()).orElseThrow();
        assertEquals(OrigemResponsavel.PACIENTE, r.getOrigem());
        assertEquals(2, r.getPermissoes().size(), "as duas funcionalidades escolhidas");
        assertEquals(NivelAcessoResponsavel.VISUALIZAR_LANCAR, r.getPermissoes().get(FuncionalidadeApp.AGENDAMENTOS));
        assertEquals(NivelAcessoResponsavel.VISUALIZAR, r.getPermissoes().get(FuncionalidadeApp.CHAT));

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
                () -> controller.adicionar(jwt, new MeuResponsavelRequest("Outro", CPF_RESP_B, DOB_RESP, "11988881111", Map.of())))
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
                .adicionar(jwt, new MeuResponsavelRequest("Bia Ajuda", CPF_RESP_A, DOB_RESP, "(11) 97777-2222", Map.of(FuncionalidadeApp.AGENDAMENTOS, NivelAcessoResponsavel.VISUALIZAR_LANCAR))).getBody();
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
                () -> controller.adicionar(jwt, new MeuResponsavelRequest("Eu mesmo", CPF_RESP_A, DOB_RESP, TEL, Map.of())))
                .getStatusCode().value());
    }

    @Test
    void telefoneInvalidoEhRejeitado() {
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> controller.adicionar(jwt, new MeuResponsavelRequest("Curto", CPF_RESP_A, DOB_RESP, "123", Map.of())))
                .getStatusCode().value());
    }

    @Test
    void editarAlteraDadosEPermissoesComAuditoria() {
        MeuResponsavelResponse criado = controller.adicionar(jwt,
                new MeuResponsavelRequest("Carlos", CPF_RESP_A, DOB_RESP, "(11) 96666-1111",
                        Map.of(FuncionalidadeApp.AGENDAMENTOS, NivelAcessoResponsavel.VISUALIZAR))).getBody();
        assertNotNull(criado);

        MeuResponsavelResponse editado = controller.editar(jwt, criado.id(),
                new MeuResponsavelEditarRequest("Carlos Alberto", DOB_RESP, "(11) 95555-2222",
                        Map.of(FuncionalidadeApp.CHAT, NivelAcessoResponsavel.VISUALIZAR_LANCAR,
                                FuncionalidadeApp.PRONTUARIO, NivelAcessoResponsavel.VISUALIZAR)));
        assertEquals("Carlos Alberto", editado.nome());
        assertEquals("11955552222", editado.telefone(), "telefone atualizado e normalizado");
        assertEquals(2, editado.permissoes().size(), "novas permissões substituem as anteriores");
        assertEquals(NivelAcessoResponsavel.VISUALIZAR_LANCAR, editado.permissoes().get(FuncionalidadeApp.CHAT));
        assertEquals(NivelAcessoResponsavel.VISUALIZAR, editado.permissoes().get(FuncionalidadeApp.PRONTUARIO));
        assertNull(editado.permissoes().get(FuncionalidadeApp.AGENDAMENTOS), "Agendamentos foi retirado");

        // Persistiu na entidade.
        Responsavel r = responsavelRepository.findById(criado.id()).orElseThrow();
        assertEquals("Carlos Alberto", r.getNome());
        assertEquals(NivelAcessoResponsavel.VISUALIZAR_LANCAR, r.getPermissoes().get(FuncionalidadeApp.CHAT));

        // Auditoria (LGPD): a edição vira um evento de ALTERAÇÃO do app (autor PACIENTE).
        var logs = pacienteLogService.listar(pacienteId).stream()
                .filter(l -> l.autor() == AutorLogPaciente.PACIENTE).toList();
        assertTrue(logs.stream().anyMatch(l -> l.alteracoes().stream().anyMatch(
                a -> "RESPONSAVEL".equals(a.campo()) && a.valorDepois() != null
                        && a.valorDepois().contains("Carlos Alberto"))),
                "linha 'Responsável alterado' auditada");
    }
}
