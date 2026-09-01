package com.example.pop.sau;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Ref;
import com.example.pop.paciente.Paciente;
import com.example.pop.push.PushService;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

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
    private final PushService pushService;

    public SauService(ManifestacaoRepository repository, ManifestacaoMensagemRepository mensagemRepository,
            UnidadeRepository unidadeRepository, TipoManifestacaoRepository tipoRepository, PushService pushService) {
        this.repository = repository;
        this.mensagemRepository = mensagemRepository;
        this.unidadeRepository = unidadeRepository;
        this.tipoRepository = tipoRepository;
        this.pushService = pushService;
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
        criarMensagem(m, AutorManifestacao.PACIENTE, null, null, texto);
        return m;
    }

    /**
     * Paciente responde (1 mensagem por vez). Só é a vez do paciente quando está
     * "aguardando paciente" ou "fechada" (respondendo, reabre). Se estiver
     * "aguardando SAU", o paciente já enviou e precisa esperar — 409.
     */
    public Manifestacao responderComoPaciente(Manifestacao m, String texto) {
        if (m.getStatus() == StatusManifestacao.AGUARDANDO_SAU) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aguarde a resposta do SAU antes de enviar outra mensagem.");
        }
        criarMensagem(m, AutorManifestacao.PACIENTE, null, null, texto);
        m.setStatus(StatusManifestacao.AGUARDANDO_SAU);
        m.setAtualizadoEm(LocalDateTime.now());
        return salvarComVersao(m);
    }

    /**
     * SAU (admin) responde (1 mensagem por vez): só quando é a vez do SAU
     * ("aguardando SAU"). Fora disso — aguardando o paciente ou fechada — 409.
     * Ao responder, status vai para "aguardando paciente" e o paciente é notificado.
     */
    public Manifestacao responderComoSau(Manifestacao m, Long usuarioId, String usuarioNome, String texto) {
        if (m.getStatus() != StatusManifestacao.AGUARDANDO_SAU) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    m.getStatus() == StatusManifestacao.FECHADA
                            ? "Manifestação fechada. Somente o paciente pode reabrir."
                            : "Aguarde a resposta do paciente antes de enviar outra mensagem.");
        }
        criarMensagem(m, AutorManifestacao.SAU, usuarioId, usuarioNome, texto);
        m.setStatus(StatusManifestacao.AGUARDANDO_PACIENTE);
        m.setAtualizadoEm(LocalDateTime.now());
        // Grava (checando a versão) ANTES do push: se perder a corrida, dá 409 e
        // não dispara notificação de uma mensagem que não foi persistida.
        salvarComVersao(m);
        pushService.notificarPaciente(m.getPaciente().getId(), "Resposta do SAU",
                "O atendimento respondeu sua manifestação (" + m.getTipo().getNome() + ").",
                Map.of("tipo", "SAU", "manifestacaoId", m.getId()));
        return m;
    }

    /** SAU marca como fechada (só o SAU fecha; pode fechar a qualquer momento enquanto aberta). */
    public Manifestacao fechar(Manifestacao m) {
        if (m.getStatus() == StatusManifestacao.FECHADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manifestação já está fechada.");
        }
        m.setStatus(StatusManifestacao.FECHADA);
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

    private void criarMensagem(Manifestacao m, AutorManifestacao autor, Long usuarioId, String usuarioNome,
            String texto) {
        ManifestacaoMensagem msg = new ManifestacaoMensagem();
        msg.setManifestacao(m);
        msg.setAutor(autor);
        msg.setUsuarioId(usuarioId);
        msg.setUsuarioNome(usuarioNome);
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
                m.getAtualizadoEm(), m.getCriadoEm());
    }

    /**
     * Detalhe + thread. {@code revelarAtendente}=true (CRUD do admin) mostra o nome
     * do atendente que respondeu; =false (app do paciente) mostra "Atendimento SAU".
     */
    public ManifestacaoDetalheResponse toDetalhe(Manifestacao m, boolean revelarAtendente) {
        List<MensagemSauResponse> mensagens = mensagemRepository.findByManifestacaoIdOrderByCriadoEmAsc(m.getId())
                .stream().map(msg -> toMensagem(m, msg, revelarAtendente)).toList();
        return new ManifestacaoDetalheResponse(
                m.getId(),
                new Ref(m.getPaciente().getId(), m.getPaciente().getNome()),
                new Ref(m.getUnidadeSaude().getId(), m.getUnidadeSaude().getNome()),
                new Ref(m.getTipo().getId(), m.getTipo().getNome()),
                m.getStatus(), m.getStatus().getDescricao(),
                mensagens);
    }

    private MensagemSauResponse toMensagem(Manifestacao m, ManifestacaoMensagem msg, boolean revelarAtendente) {
        String autorNome;
        if (msg.getAutor() == AutorManifestacao.SAU) {
            autorNome = revelarAtendente && msg.getUsuarioNome() != null ? msg.getUsuarioNome() : "Atendimento SAU";
        } else {
            autorNome = m.getPaciente().getNome();
        }
        return new MensagemSauResponse(msg.getId(), msg.getAutor(), autorNome, msg.getTexto(), msg.getCriadoEm());
    }
}
