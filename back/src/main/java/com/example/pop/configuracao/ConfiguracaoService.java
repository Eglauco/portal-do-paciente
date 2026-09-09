package com.example.pop.configuracao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.pop.common.Pagina;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

/**
 * CRUD (só edição do valor) das configurações + ACESSO TIPADO pela chave para as
 * regras de negócio. Config é MUITO lida e POUCO alterada, então há um cache em
 * memória (por chave) carregado sob demanda e invalidado a cada alteração.
 */
@Service
public class ConfiguracaoService {

    private static final int TAMANHO_MAXIMO = 100;

    private final ConfiguracaoRepository repository;
    private final UsuarioRepository usuarioRepository;

    /** Cache por chave (snapshot desanexado). Null = precisa recarregar. */
    private volatile Map<String, Configuracao> cachePorChave;

    public ConfiguracaoService(ConfiguracaoRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    // ---------- CRUD (tela) ----------

    @Transactional(readOnly = true)
    public Pagina<ConfiguracaoResponse> listar(String busca, TipoConfiguracao tipo, int page, int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho, Sort.by(Sort.Direction.ASC, "nome"));
        Page<Configuracao> resultado = repository.search(busca == null ? "" : busca.trim(), tipo, pageable);
        Map<Long, String> nomes = nomesEditores(resultado.getContent());
        List<ConfiguracaoResponse> content = resultado.getContent().stream()
                // Guarda o null: nomes é Map.of() quando ninguém editou, e Map.of().get(null) estoura NPE.
                .map(c -> ConfiguracaoResponse.from(c,
                        c.getAtualizadoPor() == null ? null : nomes.get(c.getAtualizadoPor())))
                .toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    @Transactional(readOnly = true)
    public Optional<ConfiguracaoResponse> buscar(Long id) {
        return repository.findById(id).map(c -> ConfiguracaoResponse.from(c, nomeEditor(c.getAtualizadoPor())));
    }

    /** Atualiza SÓ o valor (o campo do tipo do registro) + auditoria; invalida o cache. */
    @Transactional
    public Optional<ConfiguracaoResponse> atualizarValor(Long id, ConfiguracaoRequest req, Long usuarioId) {
        return repository.findById(id).map(c -> {
            switch (c.getTipoConfiguracao()) {
                case BOOLEANO -> c.setValorBooleano(Boolean.TRUE.equals(req.valorBooleano()));
                // Ajusta a escala (4) para bater com o NUMERIC(19,4) do banco — a resposta então
                // reflete exatamente o valor persistido (sem divergência por arredondamento).
                case NUMERICO -> c.setValorNumerico(req.valorNumerico() == null ? null
                        : req.valorNumerico().setScale(4, RoundingMode.HALF_UP));
                case TEXTO -> c.setValorTexto(req.valorTexto());
                case COR -> c.setValorCor(normalizarCor(req.valorCor()));
            }
            c.setAtualizadoEm(LocalDateTime.now());
            c.setAtualizadoPor(usuarioId);
            Configuracao salvo = repository.save(c);
            // Invalida o cache SÓ APÓS o commit: se invalidasse agora (dentro da transação), uma
            // leitura concorrente poderia recarregar o valor ANTIGO (ainda commitado) e servi-lo.
            invalidarCacheAposCommit();
            return ConfiguracaoResponse.from(salvo, nomeEditor(usuarioId));
        });
    }

    /** Invalida o cache após o commit da transação (ou já, se não houver transação ativa). */
    private void invalidarCacheAposCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidarCache();
                }
            });
        } else {
            invalidarCache();
        }
    }

    // ---------- Acesso tipado (regra de negócio) ----------

    /** Valor booleano da chave (false se nulo). Erro se a chave não existir ou o tipo divergir. */
    public boolean lerBooleano(String chave) {
        return Boolean.TRUE.equals(obrigatoria(chave, TipoConfiguracao.BOOLEANO).getValorBooleano());
    }

    /** Valor numérico da chave (pode ser null). Erro se a chave não existir ou o tipo divergir. */
    public BigDecimal lerNumerico(String chave) {
        return obrigatoria(chave, TipoConfiguracao.NUMERICO).getValorNumerico();
    }

    /** Valor texto da chave (pode ser null). Erro se a chave não existir ou o tipo divergir. */
    public String lerTexto(String chave) {
        return obrigatoria(chave, TipoConfiguracao.TEXTO).getValorTexto();
    }

    /** Valor cor (hex {@code #RRGGBB}) da chave. Erro se a chave não existir ou o tipo divergir. */
    public String lerCor(String chave) {
        return obrigatoria(chave, TipoConfiguracao.COR).getValorCor();
    }

    /** Normaliza e valida uma cor {@code #RRGGBB} (maiúscula); 422 se o formato for inválido. */
    private static String normalizarCor(String cor) {
        String limpo = cor == null ? "" : cor.trim().toUpperCase();
        if (!limpo.matches("^#[0-9A-F]{6}$")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cor inválida. Use o formato #RRGGBB (ex.: #0E8C7F).");
        }
        return limpo;
    }

    /**
     * Config da chave, garantindo o tipo esperado. Falha-rápido (IllegalStateException) se a
     * chave não existe (faltou a migration de INSERT) ou o tipo não bate — são erros de
     * desenvolvimento, não devem passar silenciosos.
     */
    private Configuracao obrigatoria(String chave, TipoConfiguracao tipoEsperado) {
        Configuracao c = cache().get(chave);
        if (c == null) {
            throw new IllegalStateException(
                    "Configuração não encontrada: '" + chave + "'. Faltou a migration de INSERT?");
        }
        if (c.getTipoConfiguracao() != tipoEsperado) {
            throw new IllegalStateException("Configuração '" + chave + "' é do tipo " + c.getTipoConfiguracao()
                    + ", mas foi lida como " + tipoEsperado + ".");
        }
        return c;
    }

    private Map<String, Configuracao> cache() {
        Map<String, Configuracao> local = cachePorChave;
        if (local == null) {
            synchronized (this) {
                local = cachePorChave;
                if (local == null) {
                    local = repository.findAll().stream()
                            .collect(Collectors.toMap(Configuracao::getChave, c -> c));
                    cachePorChave = local;
                }
            }
        }
        return local;
    }

    public void invalidarCache() {
        cachePorChave = null;
    }

    // ---------- auxiliares ----------

    private Map<Long, String> nomesEditores(List<Configuracao> lista) {
        Set<Long> ids = lista.stream().map(Configuracao::getAtualizadoPor)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of()
                : usuarioRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
    }

    private String nomeEditor(Long usuarioId) {
        return usuarioId == null ? null
                : usuarioRepository.findById(usuarioId).map(Usuario::getNome).orElse(null);
    }
}
