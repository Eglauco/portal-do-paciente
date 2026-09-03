package com.example.pop.sau;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Ref;
import com.example.pop.notificacao.NotificacaoService;
import com.example.pop.notificacao.TipoNotificacao;
import com.example.pop.paciente.Paciente;
import com.example.pop.push.PushService;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Regras do SAU (Serviço de Atendimento ao Usuário): abertura de manifestações,
 * respostas de paciente/SAU, fechamento e mapeamentos. Cada mensagem guarda autor
 * e data/hora; as do SAU guardam também o admin (id + nome) que respondeu.
 */
@Service
public class SauService {

    private final ManifestacaoRepository repository;
    private final ManifestacaoMensagemRepository mensagemRepository;
    private final UnidadeRepository unidadeRepository;
    private final TipoManifestacaoRepository tipoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PushService pushService;
    private final NotificacaoService notificacaoService;

    public SauService(ManifestacaoRepository repository, ManifestacaoMensagemRepository mensagemRepository,
            UnidadeRepository unidadeRepository, TipoManifestacaoRepository tipoRepository,
            UsuarioRepository usuarioRepository, PushService pushService, NotificacaoService notificacaoService) {
        this.repository = repository;
        this.mensagemRepository = mensagemRepository;
        this.unidadeRepository = unidadeRepository;
        this.tipoRepository = tipoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pushService = pushService;
        this.notificacaoService = notificacaoService;
    }

    /** Abre uma manifestação (cria + a 1ª mensagem do paciente). Status inicial: aguardando SAU. */
    public Manifestacao abrir(Paciente paciente, Long unidadeId, Long tipoId, String texto) {
        Unidade unidade = unidadeRepository.findById(unidadeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unidade não encontrada"));
        TipoManifestacao tipo = tipoRepository.findById(tipoId)
                .filter(TipoManifestacao::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tipo de manifestação inválido ou desativado"));
        LocalDateTime agora = LocalDateTime.now();
        Manifestacao m = new Manifestacao();
        m.setPaciente(paciente);
        m.setUnidadeSaude(unidade);
        m.setTipo(tipo);
        m.setStatus(StatusManifestacao.AGUARDANDO_SAU);
        m.setCriadoEm(agora);
        m.setAtualizadoEm(agora);
        repository.save(m);
        criarMensagem(m, AutorManifestacao.PACIENTE, null, texto);
        return m;
    }

    /**
     * Paciente responde (1 mensagem por vez). Só é a vez do paciente quando está
     * "aguardando paciente" ou "fechada" (respondendo, reabre). Se estiver
     * "aguardando SAU", o paciente já enviou e precisa esperar — 409.
     */
    public Manifestacao responderComoPaciente(Manifestacao m, String texto) {
        if (m.getAvaliadoEm() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta conversa foi encerrada e avaliada; não é possível reabri-la.");
        }
        if (m.getStatus() == StatusManifestacao.AGUARDANDO_SAU) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aguarde a resposta do SAU antes de enviar outra mensagem.");
        }
        criarMensagem(m, AutorManifestacao.PACIENTE, null, texto);
        m.setStatus(StatusManifestacao.AGUARDANDO_SAU);
        m.setAtualizadoEm(LocalDateTime.now());
        return salvarComVersao(m);
    }

    /**
     * SAU (admin) responde (1 mensagem por vez): só quando é a vez do SAU
     * ("aguardando SAU"). Fora disso — aguardando o paciente ou fechada — 409.
     * Ao responder, status vai para "aguardando paciente" e o paciente é notificado.
     */
    public Manifestacao responderComoSau(Manifestacao m, Long usuarioId, String texto) {
        if (m.getStatus() != StatusManifestacao.AGUARDANDO_SAU) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    m.getStatus() == StatusManifestacao.FECHADA
                            ? "Manifestação fechada. Somente o paciente pode reabrir."
                            : "Aguarde a resposta do paciente antes de enviar outra mensagem.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        criarMensagem(m, AutorManifestacao.SAU, usuario, texto);
        m.setStatus(StatusManifestacao.AGUARDANDO_PACIENTE);
        m.setAtualizadoEm(LocalDateTime.now());
        // Grava (checando a versão) ANTES do push: se perder a corrida, dá 409 e
        // não dispara notificação de uma mensagem que não foi persistida.
        salvarComVersao(m);
        // Captura os dados AINDA na transação (tipo/paciente são LAZY) e só grava a
        // notificação + dispara o push DEPOIS do commit. Como registrar roda em
        // REQUIRES_NEW (comita na hora), fazê-lo antes do commit poderia deixar uma
        // notificação órfã se o tx de negócio revertesse — igual ao padrão do chat.
        String corpoSau = "O atendimento respondeu sua manifestação (" + m.getTipo().getNome() + ").";
        Long pacienteId = m.getPaciente().getId();
        Long manifestacaoId = m.getId();
        aposCommit(() -> {
            notificacaoService.registrar(pacienteId, TipoNotificacao.SAU, "Resposta do SAU", corpoSau, manifestacaoId);
            pushService.notificarPaciente(pacienteId, "Resposta do SAU", corpoSau,
                    Map.of("tipo", "SAU", "manifestacaoId", manifestacaoId));
        });
        return m;
    }

    /**
     * SAU marca como fechada (pode fechar a qualquer momento enquanto aberta). O
     * paciente é avisado para AVALIAR o atendimento (obrigatório para finalizar) ou
     * REABRIR a conversa — enquanto não avaliar, ainda pode responder e reabrir.
     */
    public Manifestacao fechar(Manifestacao m) {
        if (m.getStatus() == StatusManifestacao.FECHADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manifestação já está fechada.");
        }
        m.setStatus(StatusManifestacao.FECHADA);
        m.setAtualizadoEm(LocalDateTime.now());
        Manifestacao salva = salvarComVersao(m);
        String corpo = "Sua manifestação (" + m.getTipo().getNome()
                + ") foi encerrada pelo SAU. Avalie o atendimento ou reabra a conversa.";
        Long pacienteId = m.getPaciente().getId();
        Long manifestacaoId = m.getId();
        aposCommit(() -> {
            notificacaoService.registrar(pacienteId, TipoNotificacao.SAU, "Manifestação encerrada", corpo, manifestacaoId);
            pushService.notificarPaciente(pacienteId, "Manifestação encerrada", corpo,
                    Map.of("tipo", "SAU", "manifestacaoId", manifestacaoId));
        });
        return salva;
    }

    /**
     * Paciente encerra a conversa avaliando o atendimento (nota 1-5 obrigatória +
     * comentário opcional). A partir daqui a manifestação fica FECHADA e avaliada —
     * definitiva, não reabre. Só é possível avaliar uma vez.
     */
    public Manifestacao encerrarPeloPaciente(Manifestacao m, int nota, String comentario) {
        if (m.getAvaliadoEm() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta conversa já foi encerrada e avaliada.");
        }
        String texto = comentario == null || comentario.isBlank() ? null : comentario.trim();
        m.setStatus(StatusManifestacao.FECHADA);
        m.setAvaliacaoNota(nota);
        m.setAvaliacaoComentario(texto);
        m.setAvaliadoEm(LocalDateTime.now());
        m.setAtualizadoEm(LocalDateTime.now());
        return salvarComVersao(m);
    }

    /**
     * Grava forçando a checagem da versão (@Version). Se outra transação alterou a
     * manifestação nesse meio-tempo, devolve 409 em vez de sobrescrever — é o que
     * fecha a janela de "check-then-act" da regra de 1 mensagem por vez.
     */
    private Manifestacao salvarComVersao(Manifestacao m) {
        try {
            return repository.saveAndFlush(m);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A manifestação acabou de ser atualizada. Recarregue e tente novamente.");
        }
    }

    /** Executa a ação após o commit da transação atual (ou imediatamente, se não houver). */
    private void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }

    private void criarMensagem(Manifestacao m, AutorManifestacao autor, Usuario usuario, String texto) {
        ManifestacaoMensagem msg = new ManifestacaoMensagem();
        msg.setManifestacao(m);
        msg.setAutor(autor);
        msg.setUsuario(usuario);
        msg.setTexto(texto.trim());
        msg.setCriadoEm(LocalDateTime.now());
        mensagemRepository.save(msg);
    }

    public ManifestacaoResponse toResponse(Manifestacao m) {
        ManifestacaoMensagem ultima = mensagemRepository.findFirstByManifestacaoIdOrderByCriadoEmDesc(m.getId());
        return new ManifestacaoResponse(
                m.getId(),
                new Ref(m.getPaciente().getId(), m.getPaciente().getNome()),
                new Ref(m.getUnidadeSaude().getId(), m.getUnidadeSaude().getNome()),
                new Ref(m.getTipo().getId(), m.getTipo().getNome()),
                m.getStatus(), m.getStatus().getDescricao(),
                ultima != null ? ultima.getTexto() : null,
                ultima != null ? ultima.getAutor() : null,
                m.getAvaliacaoNota(),
                m.getAtualizadoEm(), m.getCriadoEm());
    }

    /**
     * Detalhe + thread. O nome do atendente que respondeu é sempre revelado — tanto
     * no CRUD do admin quanto no app do paciente (o paciente vê "Nome do atendente"
     * em destaque e "Atendimento SAU" como papel). Sem atendente vinculado (caso
     * raro), cai para "Atendimento SAU".
     */
    public ManifestacaoDetalheResponse toDetalhe(Manifestacao m) {
        List<MensagemSauResponse> mensagens = mensagemRepository.findByManifestacaoIdOrderByCriadoEmAsc(m.getId())
                .stream().map(msg -> toMensagem(m, msg)).toList();
        return new ManifestacaoDetalheResponse(
                m.getId(),
                new Ref(m.getPaciente().getId(), m.getPaciente().getNome()),
                new Ref(m.getUnidadeSaude().getId(), m.getUnidadeSaude().getNome()),
                new Ref(m.getTipo().getId(), m.getTipo().getNome()),
                m.getStatus(), m.getStatus().getDescricao(),
                m.getAvaliacaoNota(), m.getAvaliacaoComentario(), m.getAvaliadoEm(),
                mensagens);
    }

    private MensagemSauResponse toMensagem(Manifestacao m, ManifestacaoMensagem msg) {
        String autorNome;
        if (msg.getAutor() == AutorManifestacao.SAU) {
            autorNome = msg.getUsuario() != null ? msg.getUsuario().getNome() : "Atendimento SAU";
        } else {
            autorNome = m.getPaciente().getNome();
        }
        return new MensagemSauResponse(msg.getId(), msg.getAutor(), autorNome, msg.getTexto(), msg.getCriadoEm());
    }
}
