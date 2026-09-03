package com.example.pop.sau;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Pagina;
import com.example.pop.common.Ref;
import com.example.pop.paciente.PacienteController;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.PacienteRequest;
import com.example.pop.pacienteauth.AtivarPacienteRequest;
import com.example.pop.pacienteauth.PacienteAuthController;
import com.example.pop.unidade.UnidadeRepository;
import com.example.pop.usuario.UsuarioRepository;

@SpringBootTest
class SauControllerTest {

    private static final String TEL = "11955550001";
    private static final String NOME_ATENDENTE = "Atendente SAU Teste";

    @Autowired
    private MeuSauController meuController;
    @Autowired
    private SauController sauController;
    @Autowired
    private PacienteController pacienteController;
    @Autowired
    private PacienteAuthController authController;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private UnidadeRepository unidadeRepository;
    @Autowired
    private ManifestacaoRepository manifestacaoRepository;
    @Autowired
    private TipoManifestacaoRepository tipoRepository;
    @Autowired
    private TipoManifestacaoController tipoController;
    @Autowired
    private JwtDecoder jwtDecoder;

    private Long pacienteId;
    private Long unidadeId;
    private Long tipoId;
    private Long outroTipoId;
    private Jwt jwt;

    /** Usuário (atendente) usado nos testes: o de menor id (determinístico mesmo com o banco compartilhado). */
    private com.example.pop.usuario.Usuario atendente() {
        return usuarioRepository.findAll(Sort.by("id")).get(0);
    }

    /** Token de admin com o uid do atendente. O nome agora vem da relação; o claim é só compat. */
    private Jwt adminJwt() {
        return Jwt.withTokenValue("t").header("alg", "none")
                .claim("uid", atendente().getId()).claim("nome", NOME_ATENDENTE).build();
    }

    @BeforeEach
    void setup() {
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> apagarManifestacoes(p.getId()));
        pacienteRepository.findByTelefone(TEL).ifPresent(p -> pacienteRepository.deleteById(p.getId()));
        pacienteId = pacienteController.criar(new PacienteRequest("Ana Manifestante", TEL)).getId();
        String codigo = pacienteController.gerarCodigo(pacienteId).getBody().codigo();
        jwt = jwtDecoder.decode(authController.ativar(new AtivarPacienteRequest(TEL, codigo, "dev-sau")).token());
        unidadeId = unidadeRepository.findAll().get(0).getId();
        List<TipoManifestacao> tipos = tipoRepository.findByAtivoTrueOrderByNome();
        tipoId = tipos.get(0).getId();
        outroTipoId = tipos.get(1).getId();
    }

    @AfterEach
    void limpar() {
        apagarManifestacoes(pacienteId);
        pacienteRepository.deleteById(pacienteId);
    }

    private void apagarManifestacoes(Long pid) {
        manifestacaoRepository.findByPacienteIdOrderByAtualizadoEmDesc(pid, PageRequest.of(0, 100))
                .forEach(m -> manifestacaoRepository.deleteById(m.getId()));
    }

    @Test
    void unidadesEtiposDisponiveisParaOPaciente() {
        List<Ref> unidades = meuController.unidades();
        assertFalse(unidades.isEmpty());
        assertTrue(unidades.stream().anyMatch(u -> u.id().equals(unidadeId)));

        List<TipoManifestacaoResponse> tipos = meuController.tipos();
        assertFalse(tipos.isEmpty());
        assertTrue(tipos.stream().allMatch(TipoManifestacaoResponse::ativo));
        assertTrue(tipos.stream().anyMatch(t -> t.id().equals(tipoId)));
    }

    @Test
    void fluxoCompletoAbrirResponderFechar() {
        ManifestacaoDetalheResponse aberta = meuController.abrir(jwt,
                new AbrirManifestacaoRequest(tipoId, unidadeId, "A recepção estava lenta."));
        Long id = aberta.id();
        assertNotNull(id);
        assertEquals(tipoId, aberta.tipo().id());
        assertEquals(StatusManifestacao.AGUARDANDO_SAU, aberta.status());
        assertEquals(1, aberta.mensagens().size());
        assertEquals(AutorManifestacao.PACIENTE, aberta.mensagens().get(0).autor());

        // Aparece na listagem do paciente.
        Pagina<ManifestacaoResponse> minhas = meuController.listar(jwt, 0, 20);
        assertTrue(minhas.content().stream().anyMatch(m -> m.id().equals(id)));

        // Aparece no back-office (admin) e o detalhe revela o nome do paciente.
        ManifestacaoDetalheResponse visaoAdmin = sauController.buscar(id).getBody();
        assertNotNull(visaoAdmin);
        assertEquals("Ana Manifestante", visaoAdmin.paciente().nome());

        // SAU responde: status vai para "aguardando paciente" e registra o atendente.
        ManifestacaoDetalheResponse respondida = sauController.responder(id,
                new MensagemSauRequest("Obrigado pelo retorno, vamos verificar."), adminJwt());
        assertEquals(StatusManifestacao.AGUARDANDO_PACIENTE, respondida.status());
        assertEquals(2, respondida.mensagens().size());
        MensagemSauResponse doSau = respondida.mensagens().get(1);
        assertEquals(AutorManifestacao.SAU, doSau.autor());
        // O nome agora vem da relação com `usuario` (mesmo atendente que o adminJwt usa), não do claim.
        assertEquals(atendente().getNome(), doSau.autorNome());

        // O paciente agora também vê o nome do atendente que respondeu (no app: nome em
        // negrito + "Atendimento SAU" como papel).
        ManifestacaoDetalheResponse visaoPaciente = meuController.buscar(jwt, id);
        assertEquals(atendente().getNome(), visaoPaciente.mensagens().get(1).autorNome());

        // Paciente responde: volta para "aguardando SAU".
        ManifestacaoDetalheResponse reaberta = meuController.responder(jwt, id,
                new MensagemSauRequest("Certo, aguardo o retorno."));
        assertEquals(StatusManifestacao.AGUARDANDO_SAU, reaberta.status());
        assertEquals(3, reaberta.mensagens().size());

        // SAU fecha.
        ManifestacaoDetalheResponse fechada = sauController.fechar(id);
        assertEquals(StatusManifestacao.FECHADA, fechada.status());

        // Paciente responde depois de fechada → reabre (fechar não adiciona mensagem).
        ManifestacaoDetalheResponse reabertaPosFechamento = meuController.responder(jwt, id,
                new MensagemSauRequest("Ainda tenho uma dúvida."));
        assertEquals(StatusManifestacao.AGUARDANDO_SAU, reabertaPosFechamento.status());
        assertEquals(4, reabertaPosFechamento.mensagens().size());
    }

    @Test
    void pacienteEncerraEAvaliaEProibeReabrir() {
        ManifestacaoDetalheResponse aberta = meuController.abrir(jwt,
                new AbrirManifestacaoRequest(tipoId, unidadeId, "Quero encerrar mais tarde."));
        Long id = aberta.id();

        // SAU responde: fica "aguardando paciente" (é a vez do paciente).
        sauController.responder(id, new MensagemSauRequest("Retorno do SAU."), adminJwt());

        // Paciente encerra avaliando (nota 5 + comentário) → FECHADA e avaliada.
        ManifestacaoDetalheResponse encerrada = meuController.encerrar(jwt, id,
                new EncerrarSauRequest(5, "Ótimo atendimento"));
        assertEquals(StatusManifestacao.FECHADA, encerrada.status());
        assertEquals(5, encerrada.avaliacaoNota());
        assertEquals("Ótimo atendimento", encerrada.avaliacaoComentario());
        assertNotNull(encerrada.avaliadoEm());

        // Avaliada = definitiva: reabrir (responder) e reavaliar dão 409.
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> meuController.responder(jwt, id, new MensagemSauRequest("Reabrir?")))
                .getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> meuController.encerrar(jwt, id, new EncerrarSauRequest(3, null)))
                .getStatusCode().value());
    }

    @Test
    void unidadeInvalidaAoAbrirRetorna400() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> meuController.abrir(jwt, new AbrirManifestacaoRequest(tipoId, 999999L, "texto")))
                .getStatusCode().value());
    }

    @Test
    void tipoInvalidoAoAbrirRetorna400() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> meuController.abrir(jwt, new AbrirManifestacaoRequest(999999L, unidadeId, "texto")))
                .getStatusCode().value());
    }

    @Test
    void pacienteNaoVeManifestacaoDeOutro() {
        Long id = meuController.abrir(jwt,
                new AbrirManifestacaoRequest(tipoId, unidadeId, "Sugiro mais cadeiras.")).id();

        String tel2 = "11955550002";
        pacienteRepository.findByTelefone(tel2).ifPresent(p -> pacienteRepository.deleteById(p.getId()));
        Long outroId = pacienteController.criar(new PacienteRequest("Outro Paciente", tel2)).getId();
        String cod2 = pacienteController.gerarCodigo(outroId).getBody().codigo();
        Jwt jwtOutro = jwtDecoder.decode(
                authController.ativar(new AtivarPacienteRequest(tel2, cod2, "dev-outro-sau")).token());

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> meuController.buscar(jwtOutro, id)).getStatusCode().value());

        pacienteRepository.deleteById(outroId);
    }

    @Test
    void pacienteNaoEnviaDuasMensagensSeguidas() {
        Long id = meuController.abrir(jwt,
                new AbrirManifestacaoRequest(tipoId, unidadeId, "Primeira.")).id();
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> meuController.responder(jwt, id, new MensagemSauRequest("Segunda seguida.")))
                .getStatusCode().value());
    }

    @Test
    void sauNaoEnviaDuasMensagensSeguidas() {
        Long id = meuController.abrir(jwt,
                new AbrirManifestacaoRequest(tipoId, unidadeId, "Abertura.")).id();
        sauController.responder(id, new MensagemSauRequest("Resposta 1."), adminJwt());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> sauController.responder(id, new MensagemSauRequest("Resposta 2 seguida."), adminJwt()))
                .getStatusCode().value());
    }

    @Test
    void sauNaoRespondeManifestacaoFechada() {
        Long id = meuController.abrir(jwt,
                new AbrirManifestacaoRequest(tipoId, unidadeId, "Obrigada!")).id();
        sauController.responder(id, new MensagemSauRequest("De nada."), adminJwt());
        meuController.responder(jwt, id, new MensagemSauRequest("Ok."));
        sauController.fechar(id);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> sauController.responder(id, new MensagemSauRequest("Depois de fechada."), adminJwt()))
                .getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> sauController.fechar(id)).getStatusCode().value());
    }

    @Test
    void sauPodeFecharEnquantoAguardaPaciente() {
        Long id = meuController.abrir(jwt,
                new AbrirManifestacaoRequest(tipoId, unidadeId, "Abertura.")).id();
        sauController.responder(id, new MensagemSauRequest("Resposta."), adminJwt()); // → aguardando paciente
        ManifestacaoDetalheResponse fechada = sauController.fechar(id);
        assertEquals(StatusManifestacao.FECHADA, fechada.status());
    }

    @Test
    void tipoEmUsoNaoPodeSerExcluido() {
        // Abre uma manifestação com o tipo → o cadastro não deixa excluí-lo (409); desative-o.
        meuController.abrir(jwt, new AbrirManifestacaoRequest(tipoId, unidadeId, "Usando o tipo."));
        assertEquals(409, tipoController.excluir(tipoId).getStatusCode().value());
    }

    @Test
    void adminFiltraPorTipoEStatus() {
        Long id = meuController.abrir(jwt,
                new AbrirManifestacaoRequest(tipoId, unidadeId, "Ótimo atendimento!")).id();

        Pagina<ManifestacaoResponse> porTipo = sauController.listar(null, tipoId,
                StatusManifestacao.AGUARDANDO_SAU, 0, 50);
        assertTrue(porTipo.content().stream().anyMatch(m -> m.id().equals(id)));

        // Filtro por OUTRO tipo não traz esta manifestação.
        Pagina<ManifestacaoResponse> outroTipo = sauController.listar(null, outroTipoId, null, 0, 50);
        assertFalse(outroTipo.content().stream().anyMatch(m -> m.id().equals(id)));
    }
}
