package com.example.pop.plataforma;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.configuracao.ChaveConfiguracao;

/**
 * Leitura/escrita da identidade de PLATAFORMA (schema {@code public}). É o default do PRÉ-LOGIN e o
 * FALLBACK por-campo dos inquilinos (consumido por {@code TemaService}/{@code MarcaService}).
 *
 * <p>Diferente do {@code ConfiguracaoService} (cache POR-INQUILINO), aqui o cache é ÚNICO/GLOBAL: a
 * config de plataforma é uma só, vive no public e NÃO depende do {@code TenantContext}. As leituras
 * são FAIL-OPEN (retornam {@code null} se a chave faltar) — quem consome decide o fail-safe final
 * (cor padrão / texto hardcoded), para a tela nunca quebrar.
 */
@Service
public class ConfiguracaoPlataformaService {

    private final ConfiguracaoPlataformaRepository repository;

    /** Cache único (config de plataforma é global). {@code null} = recarrega sob demanda. */
    private volatile Map<String, ConfiguracaoPlataforma> cache;

    public ConfiguracaoPlataformaService(ConfiguracaoPlataformaRepository repository) {
        this.repository = repository;
    }

    // ---------- leitura (fail-open: null se ausente) ----------

    /** Cor {@code #RRGGBB} da chave, ou {@code null} se ausente/vazia. */
    public String valorCor(String chave) {
        ConfiguracaoPlataforma c = cache().get(chave);
        return c == null ? null : c.getValorCor();
    }

    /** Texto da chave, ou {@code null} se ausente/vazio. */
    public String valorTexto(String chave) {
        ConfiguracaoPlataforma c = cache().get(chave);
        return c == null ? null : c.getValorTexto();
    }

    /** URL canônica da imagem da chave (S3), ou {@code null} se ausente/vazia. */
    public String valorImagem(String chave) {
        ConfiguracaoPlataforma c = cache().get(chave);
        return c == null ? null : c.getValorImagem();
    }

    // ---------- escrita (super-admin) ----------

    /**
     * Grava toda a identidade de plataforma de uma vez + auditoria; invalida o cache após o commit.
     * Cor é validada ({@code #RRGGBB}, 422 se inválida). Textos/imagens em branco viram {@code null}
     * (= "usar o default"). {@code autorId} é livre (sem FK; pode ser {@code null}).
     */
    @Transactional
    public void salvar(ConfigPlataformaRequest req, Long autorId) {
        LocalDateTime agora = LocalDateTime.now();
        atualizar(ChaveConfiguracao.COR_PRIMARIA_PLATAFORMA, c -> c.setValorCor(normalizarCor(req.corPrimaria())), autorId, agora);
        atualizar(ChaveConfiguracao.NOME_PLATAFORMA, c -> c.setValorTexto(vazioParaNulo(req.nomePlataforma())), autorId, agora);
        atualizar(ChaveConfiguracao.LOGIN_TITULO, c -> c.setValorTexto(vazioParaNulo(req.loginTitulo())), autorId, agora);
        atualizar(ChaveConfiguracao.LOGIN_SUBTITULO, c -> c.setValorTexto(vazioParaNulo(req.loginSubtitulo())), autorId, agora);
        atualizar(ChaveConfiguracao.LOGO_PLATAFORMA, c -> c.setValorImagem(vazioParaNulo(req.logoUrl())), autorId, agora);
        atualizar(ChaveConfiguracao.LOGIN_FUNDO, c -> c.setValorImagem(vazioParaNulo(req.loginFundoUrl())), autorId, agora);
        invalidarCacheAposCommit();
    }

    /** Busca-ou-cria a linha da chave, aplica a mudança e grava a auditoria. */
    private void atualizar(String chave, Consumer<ConfiguracaoPlataforma> mudanca, Long autorId, LocalDateTime agora) {
        ConfiguracaoPlataforma c = repository.findByChave(chave).orElseGet(() -> {
            ConfiguracaoPlataforma novo = new ConfiguracaoPlataforma();
            novo.setChave(chave);
            return novo;
        });
        mudanca.accept(c);
        c.setAtualizadoEm(agora);
        c.setAtualizadoPor(autorId);
        repository.save(c);
    }

    // ---------- cache ----------

    private Map<String, ConfiguracaoPlataforma> cache() {
        Map<String, ConfiguracaoPlataforma> atual = cache;
        if (atual == null) {
            synchronized (this) {
                atual = cache;
                if (atual == null) {
                    atual = repository.findAll().stream()
                            .collect(Collectors.toMap(ConfiguracaoPlataforma::getChave, c -> c));
                    cache = atual;
                }
            }
        }
        return atual;
    }

    /** Invalida o cache só APÓS o commit (evita recarregar o valor antigo durante a transação). */
    private void invalidarCacheAposCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache = null;
                }
            });
        } else {
            cache = null;
        }
    }

    // ---------- auxiliares ----------

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Normaliza e valida {@code #RRGGBB} (maiúscula); 422 se inválida. Mesmo contrato da tela por-inquilino. */
    private static String normalizarCor(String cor) {
        String limpo = cor == null ? "" : cor.trim().toUpperCase();
        if (!limpo.matches("^#[0-9A-F]{6}$")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cor inválida. Use o formato #RRGGBB (ex.: #0E8C7F).");
        }
        return limpo;
    }
}
