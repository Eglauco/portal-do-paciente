package com.example.pop.siresp;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;

/**
 * Tela "SIRESP" (registros importados do CROSS). Só LEITURA + importação por upload de XML — sem cadastro manual,
 * sem edição e sem exclusão. A lista é escopada pela unidade ativa (param {@code unidadeId}, como os demais CRUDs).
 * Sob /siresp (ADMIN; o acesso fino é pela Tela SIRESP no front).
 */
@RestController
@RequestMapping("/siresp")
public class SirespController {

    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "importadoEm", "importadoEm",
            "dataAgenda", "dataAgenda",
            "nomePaciente", "nomePaciente",
            "nomeEspecialidade", "nomeEspecialidade",
            "nomeProfissional", "nomeProfissional");
    /** Ordenação padrão: importados mais recentes primeiro. */
    private static final Sort ORDEM_PADRAO =
            Sort.by(Sort.Direction.DESC, "importadoEm").and(Sort.by(Sort.Direction.DESC, "id"));

    private final SirespRepository repository;
    private final SirespImportacaoService importacaoService;
    private final SirespConfigService configService;
    private final SirespAgendamentoService agendamentoService;
    private final SirespEnvioService envioService;

    public SirespController(SirespRepository repository, SirespImportacaoService importacaoService,
            SirespConfigService configService, SirespAgendamentoService agendamentoService,
            SirespEnvioService envioService) {
        this.repository = repository;
        this.importacaoService = importacaoService;
        this.configService = configService;
        this.agendamentoService = agendamentoService;
        this.envioService = envioService;
    }

    @GetMapping
    public Pagina<SirespResumoResponse> listar(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusSiresp status,
            @RequestParam(required = false) StatusEnvio statusEnvio,
            @RequestParam(required = false) TipoMovimento tipoMovimento,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        // Status de agendamento (derivado) → filtro por presença do agendamento; envio/movimento → colunas próprias.
        Boolean agendado = status == null ? null : status == StatusSiresp.AGENDADO;
        Page<Siresp> resultado = repository.search(unidadeId, texto(busca), agendado, statusEnvio, tipoMovimento, pageable);
        List<SirespResumoResponse> content = resultado.getContent().stream().map(SirespResumoResponse::from).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Detalhe com TODOS os campos do XML (somente leitura). */
    @GetMapping("/{id}")
    public Siresp detalhar(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado."));
    }

    /**
     * Reprocessa o registro: recalcula o diagnóstico (Log de integração) e, se todas as informações estiverem
     * resolvidas pelos códigos de integração, CRIA o agendamento (se ainda não houver). Útil depois de preencher
     * os códigos nos cadastros — o usuário vai reprocessando até tudo ficar verde. Não altera os dados do XML.
     */
    @PostMapping("/{id}/reprocessar")
    public Siresp reprocessar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return agendamentoService.processarRegistro(id, uidDoToken(jwt));
    }

    /**
     * Reenvia o XML deste registro ao Sistema de Gestão, replicando o "Post XML" do SIRESP (HTTP POST, parâmetro
     * {@code msg}). Grava o resultado no log de envio + status de envio. Não gera agendamento. Devolve se o Sistema de
     * Gestão processou + o registro atualizado. Falha de rede/recusa NÃO é erro HTTP (vem como {@code sucesso=false});
     * só configuração ausente (URL) devolve 422.
     */
    @PostMapping("/{id}/enviar")
    public SirespEnvioService.EnvioResponse enviar(@PathVariable Long id) {
        return envioService.enviar(id);
    }

    /**
     * Importa um XML do SIRESP e popula a tabela (uma linha por Mensagem). Se o envio automático estiver ligado
     * ({@code SIRESP_ENVIAR_AO_IMPORTAR}) e houver URL configurada, logo após o import (fora da transação) o
     * arquivo original é reenviado ao Sistema de Gestão (Post XML) e o resultado volta no corpo da resposta + no Log.
     */
    @PostMapping("/importar")
    public SirespImportResponse importar(
            @RequestParam("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal Jwt jwt) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Selecione um arquivo XML.");
        }
        String nome = arquivo.getOriginalFilename();
        if (nome != null && !nome.toLowerCase().endsWith(".xml")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O arquivo precisa ser um XML.");
        }
        SirespImportacaoService.Resultado r;
        try {
            r = importacaoService.importar(arquivo.getBytes(), nome, uidDoToken(jwt));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não foi possível ler o arquivo enviado.");
        }

        // Cria o agendamento de cada registro importado quando TODAS as informações estão resolvidas (fora da
        // transação do import). Também recalcula o Log de integração de cada linha. Nunca derruba o import.
        Long uid = uidDoToken(jwt);
        for (Long sirespId : r.ids()) {
            try {
                agendamentoService.processarRegistro(sirespId, uid);
            } catch (RuntimeException ignorado) {
                // falha isolada de um registro não impede o restante do import
            }
        }

        // Envio automático ao Sistema de Gestão (fora da transação do import). Nunca derruba o import: falhas viram log + flag.
        boolean enviado = false;
        boolean envioSucesso = false;
        String envioMensagem = null;
        if (configService.enviarAoImportar()) {
            SirespEnvioService.EnvioResponse e = envioService.enviarDoImport(r.arquivoUrl(), r.ids());
            enviado = e.enviado();
            envioSucesso = e.sucesso();
            envioMensagem = e.mensagem();
        }
        return new SirespImportResponse(r.importados(), r.arquivo(), r.pacientesAtualizados(),
                r.pacientesCriados(), enviado, envioSucesso, envioMensagem);
    }

    /** Configuração da atualização do paciente (liga/desliga + ação por campo) exibida no modal. */
    @GetMapping("/config")
    public SirespConfigService.Dados lerConfig() {
        return configService.ler();
    }

    @PutMapping("/config")
    public SirespConfigService.Dados salvarConfig(@RequestBody SirespConfigService.Salvar req,
            @AuthenticationPrincipal Jwt jwt) {
        return configService.salvar(req, uidDoToken(jwt));
    }

    private static Long uidDoToken(Jwt jwt) {
        return jwt != null && jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
    }

    private static String texto(String v) {
        return v == null ? "" : v.trim();
    }
}
