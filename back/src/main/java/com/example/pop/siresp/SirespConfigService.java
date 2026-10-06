package com.example.pop.siresp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.procedimento.ProcedimentoRepository;

/** Lê e salva a configuração de atualização do paciente do SIRESP (liga/desliga geral + ação por campo). */
@Service
public class SirespConfigService {

    private final SirespConfigCampoRepository repository;
    private final ConfiguracaoService configuracaoService;
    private final ProcedimentoRepository procedimentoRepository;

    public SirespConfigService(SirespConfigCampoRepository repository, ConfiguracaoService configuracaoService,
            ProcedimentoRepository procedimentoRepository) {
        this.repository = repository;
        this.configuracaoService = configuracaoService;
        this.procedimentoRepository = procedimentoRepository;
    }

    /** Um campo na resposta do modal: chave + rótulo + ação atual. */
    public record CampoConfig(String campo, String rotulo, AcaoAtualizacao acao) {
    }

    /** Uma opção do dropdown de procedimento padrão no modal. */
    public record ProcedimentoOpcao(Long id, String nome) {
    }

    public record Dados(boolean habilitado, boolean criar, String postUrl, boolean enviarAoImportar,
            Long procedimentoPadraoId, List<ProcedimentoOpcao> procedimentos, List<CampoConfig> campos) {
    }

    /** Payload do modal: atualizar + criar + URL de envio + enviar-ao-importar + procedimento padrão + campo→ação. */
    public record Salvar(boolean habilitado, boolean criar, String postUrl, boolean enviarAoImportar,
            Long procedimentoPadraoId, Map<String, AcaoAtualizacao> campos) {
    }

    @Transactional(readOnly = true)
    public Dados ler() {
        Map<String, AcaoAtualizacao> atuais = acoes();
        List<CampoConfig> campos = new ArrayList<>();
        for (CampoMapeado c : CampoMapeado.CAMPOS) {
            campos.add(new CampoConfig(c.chave(), c.rotulo(), atuais.getOrDefault(c.chave(), AcaoAtualizacao.NUNCA)));
        }
        List<ProcedimentoOpcao> procedimentos = procedimentoRepository.findAll(Sort.by("nome")).stream()
                .map(p -> new ProcedimentoOpcao(p.getId(), p.getNome())).toList();
        return new Dados(habilitado(), criar(), postUrl(), enviarAoImportar(), procedimentoPadraoId(), procedimentos,
                campos);
    }

    /** true se a atualização do paciente (quando encontrado) está ligada. */
    public boolean habilitado() {
        return configuracaoService.lerBooleano(ChaveConfiguracao.SIRESP_ATUALIZAR_PACIENTE);
    }

    /** true se deve criar o paciente quando não encontrado (por código de integração / CPF). */
    public boolean criar() {
        return configuracaoService.lerBooleano(ChaveConfiguracao.SIRESP_CRIAR_PACIENTE);
    }

    /** URL do cliente que recebe o XML via HTTP POST (null/vazia = envio desabilitado). */
    public String postUrl() {
        return configuracaoService.lerTexto(ChaveConfiguracao.SIRESP_POST_URL);
    }

    /** true se deve reenviar o XML ao cliente automaticamente ao importar (só dispara se houver URL). */
    public boolean enviarAoImportar() {
        return configuracaoService.lerBooleano(ChaveConfiguracao.SIRESP_ENVIAR_AO_IMPORTAR);
    }

    /** Id do Procedimento padrão usado nos agendamentos do SIRESP (null = não configurado). */
    public Long procedimentoPadraoId() {
        BigDecimal v = configuracaoService.lerNumerico(ChaveConfiguracao.SIRESP_PROCEDIMENTO_PADRAO_ID);
        return v == null ? null : v.longValue();
    }

    /** Mapa campo→ação (campos ausentes = NUNCA). */
    public Map<String, AcaoAtualizacao> acoes() {
        Map<String, AcaoAtualizacao> m = new LinkedHashMap<>();
        for (SirespConfigCampo c : repository.findAll()) {
            m.put(c.getCampo(), c.getAcao());
        }
        return m;
    }

    @Transactional
    public Dados salvar(Salvar req, Long usuarioId) {
        configuracaoService.salvarBooleano(ChaveConfiguracao.SIRESP_ATUALIZAR_PACIENTE, req.habilitado(), usuarioId);
        configuracaoService.salvarBooleano(ChaveConfiguracao.SIRESP_CRIAR_PACIENTE, req.criar(), usuarioId);
        configuracaoService.salvarTexto(ChaveConfiguracao.SIRESP_POST_URL, req.postUrl(), usuarioId);
        configuracaoService.salvarBooleano(ChaveConfiguracao.SIRESP_ENVIAR_AO_IMPORTAR, req.enviarAoImportar(), usuarioId);
        configuracaoService.salvarNumerico(ChaveConfiguracao.SIRESP_PROCEDIMENTO_PADRAO_ID,
                req.procedimentoPadraoId() == null ? null : BigDecimal.valueOf(req.procedimentoPadraoId()), usuarioId);
        Map<String, AcaoAtualizacao> campos = req.campos() == null ? Map.of() : req.campos();
        for (CampoMapeado c : CampoMapeado.CAMPOS) {
            AcaoAtualizacao acao = campos.getOrDefault(c.chave(), AcaoAtualizacao.NUNCA);
            SirespConfigCampo linha = repository.findByCampo(c.chave()).orElseGet(() -> {
                SirespConfigCampo nova = new SirespConfigCampo();
                nova.setCampo(c.chave());
                return nova;
            });
            linha.setAcao(acao);
            repository.save(linha);
        }
        return ler();
    }
}
