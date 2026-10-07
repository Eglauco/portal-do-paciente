package com.example.pop.siresp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;

/** Lê e salva a configuração de atualização do paciente do SIRESP (liga/desliga geral + ação por campo). */
@Service
public class SirespConfigService {

    private final SirespConfigCampoRepository repository;
    private final ConfiguracaoService configuracaoService;

    public SirespConfigService(SirespConfigCampoRepository repository, ConfiguracaoService configuracaoService) {
        this.repository = repository;
        this.configuracaoService = configuracaoService;
    }

    /** Um campo na resposta do modal: chave + rótulo + ação atual. */
    public record CampoConfig(String campo, String rotulo, AcaoAtualizacao acao) {
    }

    public record Dados(boolean habilitado, boolean criar, String postUrl, boolean enviarAoImportar,
            List<CampoConfig> campos) {
    }

    /** Payload do modal: atualizar + criar + URL de envio + enviar-ao-importar + campo→ação. */
    public record Salvar(boolean habilitado, boolean criar, String postUrl, boolean enviarAoImportar,
            Map<String, AcaoAtualizacao> campos) {
    }

    @Transactional(readOnly = true)
    public Dados ler() {
        Map<String, AcaoAtualizacao> atuais = acoes();
        List<CampoConfig> campos = new ArrayList<>();
        for (CampoMapeado c : CampoMapeado.CAMPOS) {
            campos.add(new CampoConfig(c.chave(), c.rotulo(), atuais.getOrDefault(c.chave(), AcaoAtualizacao.NUNCA)));
        }
        return new Dados(habilitado(), criar(), postUrl(), enviarAoImportar(), campos);
    }

    /** true se a atualização do paciente (quando encontrado) está ligada. */
    public boolean habilitado() {
        return configuracaoService.lerBooleano(ChaveConfiguracao.SIRESP_ATUALIZAR_PACIENTE);
    }

    /** true se deve criar o paciente quando não encontrado (por código de integração / CPF). */
    public boolean criar() {
        return configuracaoService.lerBooleano(ChaveConfiguracao.SIRESP_CRIAR_PACIENTE);
    }

    /** URL do Sistema de Gestão que recebe o XML via HTTP POST (null/vazia = envio desabilitado). */
    public String postUrl() {
        return configuracaoService.lerTexto(ChaveConfiguracao.SIRESP_POST_URL);
    }

    /** true se deve reenviar o XML ao Sistema de Gestão automaticamente ao importar (só dispara se houver URL). */
    public boolean enviarAoImportar() {
        return configuracaoService.lerBooleano(ChaveConfiguracao.SIRESP_ENVIAR_AO_IMPORTAR);
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
