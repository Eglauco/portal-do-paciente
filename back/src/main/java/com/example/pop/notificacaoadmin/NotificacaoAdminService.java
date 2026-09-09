package com.example.pop.notificacaoadmin;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.common.Pagina;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Guarda e lê as notificações do back-office (sino do admin). A GRAVAÇÃO é
 * best-effort e roda em transação própria (REQUIRES_NEW, erros engolidos):
 * notificar nunca pode quebrar nem sofrer rollback com o fluxo de negócio que a
 * originou — igual ao {@code NotificacaoService} do paciente.
 *
 * <p>Fan-out: cada evento vira UMA linha por admin ELEGÍVEL — quem tem a tela do
 * tipo E acesso à unidade do evento (união dos perfis). O "lida" é por administrador.
 */
@Service
public class NotificacaoAdminService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoAdminService.class);
    private static final int TAMANHO_MAXIMO = 100;

    private final NotificacaoAdminRepository repository;
    private final UsuarioRepository usuarioRepository;

    public NotificacaoAdminService(NotificacaoAdminRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Registra a notificação para todos os admins elegíveis (best-effort). Deduplica
     * pelo alvo: se um admin já tem uma NÃO LIDA para o mesmo {@code referenciaId},
     * não cria outra (evita spam quando o mesmo evento reaparece, ex.: re-moderação).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(TipoNotificacaoAdmin tipo, Long unidadeId, String titulo, String corpo,
            Long referenciaId, String rota) {
        if (unidadeId == null) {
            return;
        }
        try {
            List<Long> elegiveis = usuarioRepository.idsComAcesso(tipo.getTelaExigida().name(), unidadeId);
            if (elegiveis.isEmpty()) {
                return;
            }
            Set<Long> jaTem = referenciaId == null
                    ? Set.of()
                    : new HashSet<>(repository.usuariosComNaoLidaDe(tipo, referenciaId));
            LocalDateTime agora = LocalDateTime.now();
            List<NotificacaoAdmin> lote = elegiveis.stream()
                    .filter(uid -> !jaTem.contains(uid))
                    .map(uid -> montar(uid, unidadeId, tipo, titulo, corpo, referenciaId, rota, agora))
                    .toList();
            if (!lote.isEmpty()) {
                repository.saveAll(lote);
            }
        } catch (RuntimeException e) {
            // best-effort: engole para não afetar o fluxo de negócio que originou o evento
            log.warn("Falha ao registrar notificação admin (best-effort): {}", e.toString());
        }
    }

    private NotificacaoAdmin montar(Long usuarioId, Long unidadeId, TipoNotificacaoAdmin tipo, String titulo,
            String corpo, Long referenciaId, String rota, LocalDateTime agora) {
        NotificacaoAdmin n = new NotificacaoAdmin();
        n.setUsuarioId(usuarioId);
        n.setUnidadeId(unidadeId);
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setCorpo(corpo);
        n.setRota(rota);
        n.setReferenciaId(referenciaId);
        n.setLida(false);
        n.setCriadoEm(agora);
        return n;
    }

    @Transactional(readOnly = true)
    public Pagina<NotificacaoAdminResponse> listar(Long usuarioId, Long unidadeId, int page, int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        if (unidadeId == null) {
            return new Pagina<>(List.of(), pagina, tamanho, 0, 0, true, true);
        }
        Pageable pageable = PageRequest.of(pagina, tamanho);
        Page<NotificacaoAdmin> resultado = repository
                .findByUsuarioIdAndUnidadeIdOrderByCriadoEmDesc(usuarioId, unidadeId, pageable);
        List<NotificacaoAdminResponse> content = resultado.getContent().stream()
                .map(NotificacaoAdminResponse::from).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Long usuarioId, Long unidadeId) {
        return unidadeId == null ? 0 : repository.countByUsuarioIdAndUnidadeIdAndLidaFalse(usuarioId, unidadeId);
    }

    /** Marca como lida ao clicar (idempotente); ignora se não for do admin logado. */
    @Transactional
    public void marcarLida(Long id, Long usuarioId) {
        repository.findByIdAndUsuarioId(id, usuarioId).ifPresent(n -> {
            if (!n.isLida()) {
                n.setLida(true);
                n.setLidaEm(LocalDateTime.now());
                repository.save(n);
            }
        });
    }

    /** Marca TODAS as não lidas do admin na unidade ativa (botão "marcar todas como lidas"). */
    @Transactional
    public void marcarTodasLidas(Long usuarioId, Long unidadeId) {
        if (unidadeId != null) {
            repository.marcarTodasLidas(usuarioId, unidadeId, LocalDateTime.now());
        }
    }
}
