package com.example.pop.unidade;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.storage.StorageService;

@RestController
@RequestMapping("/unidade")
public class UnidadeController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist da ordenação). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "codigo", "id",
            "nome", "nome");
    /** Ordenação usada quando nada é escolhido na tela. */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.ASC, "nome", "id");

    /** Pasta no S3 das imagens do perfil (logo/capa). Não começa com "unidade-"/"inquilino-"
     *  (que são prefixos de escopo), então a validação de pasta não se confunde. */
    private static final String PASTA_PERFIL = "perfil-unidade";

    private final UnidadeRepository repository;
    private final ExportacaoService exportacaoService;
    private final StorageService storageService;
    private final GeocodingService geocoding;

    public UnidadeController(UnidadeRepository repository, ExportacaoService exportacaoService,
            StorageService storageService, GeocodingService geocoding) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
        this.storageService = storageService;
        this.geocoding = geocoding;
    }

    /**
     * Lista unidades de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Unidade> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<Unidade> resultado = repository.search(codigo, filtroNome, pageable);

        return new Pagina<>(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    /**
     * Exporta as unidades que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenadas por nome.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();

        // Ordena no BANCO (mesma collation do grid) para o export bater com a tela.
        List<Unidade> dados = repository.search(codigo, filtroNome,
                Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO))).getContent();
        List<ColunaExport<Unidade>> cols = ExportacaoService.filtrar(colunasUnidade(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Unidades", filtrosUnidade(codigo, filtroNome), cols, dados)
                : exportacaoService.excel("Unidades", cols, dados);
        String nomeArquivo = "unidades-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasUnidade().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosUnidade(Long codigo, String nome) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", (nome == null || nome.isBlank()) ? "Todos" : nome));
    }

    private static List<ColunaExport<Unidade>> colunasUnidade() {
        return List.of(
                ColunaExport.de("Código", u -> u.getId() == null ? "" : String.valueOf(u.getId())),
                ColunaExport.de("Nome", Unidade::getNome));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<UnidadeResponse> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(u -> UnidadeResponse.from(u, storageService))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UnidadeResponse criar(@RequestBody UnidadeRequest req) {
        Unidade unidade = new Unidade();
        unidade.setNome(req.nome());
        String codigo = limpar(req.codigoIntegracao());
        validarCodigoIntegracao(codigo, null);
        unidade.setCodigoIntegracao(codigo);
        aplicarPerfil(unidade, req);
        aplicarFaq(unidade, req);
        aplicarHorarios(unidade, req);
        return UnidadeResponse.from(repository.save(unidade), storageService);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<UnidadeResponse> atualizar(@PathVariable Long id, @RequestBody UnidadeRequest req) {
        return repository.findById(id)
                .map(existente -> {
                    String logoAntiga = existente.getLogoUrl();
                    String capaAntiga = existente.getCapaUrl();
                    existente.setNome(req.nome());
                    String codigo = limpar(req.codigoIntegracao());
                    validarCodigoIntegracao(codigo, id);
                    existente.setCodigoIntegracao(codigo);
                    aplicarPerfil(existente, req);
                    aplicarFaq(existente, req);
                    aplicarHorarios(existente, req);
                    UnidadeResponse resposta = UnidadeResponse.from(repository.save(existente), storageService);
                    // Remove do S3 as imagens que ficaram órfãs (trocou/removeu logo/capa).
                    Set<String> emUso = new HashSet<>();
                    adicionarSeUtil(emUso, existente.getLogoUrl());
                    adicionarSeUtil(emUso, existente.getCapaUrl());
                    excluirSeOrfa(logoAntiga, emUso);
                    excluirSeOrfa(capaAntiga, emUso);
                    return ResponseEntity.ok(resposta);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Aplica os campos de perfil (logo/capa/sobre/telefone/endereço). As imagens só podem apontar
     * para objetos da PRÓPRIA pasta de perfil no bucket (defesa: impede salvar como logo uma URL de
     * outra pasta — ex.: um laudo do prontuário — que viraria link público servido no app).
     */
    private void aplicarPerfil(Unidade unidade, UnidadeRequest req) {
        exigirNaPastaDePerfil(req.logoUrl());
        exigirNaPastaDePerfil(req.capaUrl());
        unidade.setLogoUrl(limpar(req.logoUrl()));
        unidade.setCapaUrl(limpar(req.capaUrl()));
        unidade.setSobre(limpar(req.sobre()));
        unidade.setTelefone(limpar(req.telefone()));
        String enderecoAntigo = unidade.getEndereco();
        String enderecoNovo = limpar(req.endereco());
        unidade.setEndereco(enderecoNovo);
        atualizarCoordenadas(unidade, enderecoAntigo, enderecoNovo);
    }

    /**
     * Geocodifica o endereço quando ele muda (ou ainda não há coordenadas). Endereço vazio zera as
     * coordenadas; falha na geocodificação mantém as atuais (não apaga à toa). Fail-soft: nunca
     * impede o salvamento.
     */
    private void atualizarCoordenadas(Unidade unidade, String antigo, String novo) {
        if (novo == null) {
            unidade.setLatitude(null);
            unidade.setLongitude(null);
            return;
        }
        if (!novo.equals(antigo) || unidade.getLatitude() == null) {
            geocoding.geocodificar(novo).ifPresent(c -> {
                unidade.setLatitude(c[0]);
                unidade.setLongitude(c[1]);
            });
        }
    }

    private void exigirNaPastaDePerfil(String url) {
        if (url != null && !url.isBlank() && !storageService.urlNaPasta(url, PASTA_PERFIL)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "URL de imagem inválida: envie a imagem pelo próprio formulário.");
        }
    }

    private static void adicionarSeUtil(Set<String> conjunto, String url) {
        if (url != null && !url.isBlank()) {
            conjunto.add(url);
        }
    }

    /** Remove do S3 a imagem antiga quando não é mais referenciada após o salvar (best-effort). */
    private void excluirSeOrfa(String antiga, Set<String> emUso) {
        if (antiga != null && !antiga.isBlank() && !emUso.contains(antiga)) {
            try {
                storageService.excluirPorUrl(antiga);
            } catch (RuntimeException ignore) {
                // best-effort: objeto órfão não impede o salvamento
            }
        }
    }

    /**
     * Sincroniza os horários com o payload: uma linha por dia enviado (dia nulo/repetido é ignorado).
     * ATUALIZA no lugar os dias que já existem e REMOVE os que não vieram — em vez de limpar e recriar,
     * que colidiria com o UNIQUE(unidade_id, dia_semana): o Hibernate insere a nova linha do dia ANTES
     * de apagar a órfã do mesmo dia. {@code fechado} (não abre) e {@code vinteQuatroHoras} (aberta o
     * dia inteiro) zeram abertura/fechamento; fechado tem precedência sobre 24h.
     */
    private static void aplicarHorarios(Unidade unidade, UnidadeRequest req) {
        List<UnidadeRequest.HorarioItem> itens = req.horarios() == null ? List.of() : req.horarios();
        Map<DayOfWeek, UnidadeHorario> existentes = new EnumMap<>(DayOfWeek.class);
        for (UnidadeHorario h : unidade.getHorarios()) {
            existentes.put(h.getDiaSemana(), h);
        }
        Set<DayOfWeek> enviados = EnumSet.noneOf(DayOfWeek.class);
        for (UnidadeRequest.HorarioItem item : itens) {
            if (item == null || item.diaSemana() == null || !enviados.add(item.diaSemana())) {
                continue;
            }
            UnidadeHorario h = existentes.get(item.diaSemana());
            if (h == null) {
                h = new UnidadeHorario();
                h.setUnidade(unidade);
                h.setDiaSemana(item.diaSemana());
                unidade.getHorarios().add(h);
            }
            boolean aberto24h = !item.fechado() && item.vinteQuatroHoras();
            boolean semFaixa = item.fechado() || aberto24h;
            h.setFechado(item.fechado());
            h.setVinteQuatroHoras(aberto24h);
            h.setAbertura(semFaixa ? null : item.abertura());
            h.setFechamento(semFaixa ? null : item.fechamento());
        }
        // Dias que não vieram no payload saem (sem colisão: dia removido ≠ dia inserido).
        unidade.getHorarios().removeIf(h -> !enviados.contains(h.getDiaSemana()));
    }

    /** Código de integração é único quando preenchido (identifica a unidade na integração SIRESP/CROSS). */
    private void validarCodigoIntegracao(String codigo, Long idAtual) {
        if (codigo == null) {
            return;
        }
        boolean duplicado = idAtual == null
                ? repository.existsByCodigoIntegracao(codigo)
                : repository.existsByCodigoIntegracaoAndIdNot(codigo, idAtual);
        if (duplicado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe uma unidade com este código de integração.");
        }
    }

    /** Trim; vazio vira null (para não colidir no índice único e não gravar string vazia). */
    private static String limpar(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Reconstrói o FAQ da unidade pela PRÓPRIA coleção (orphanRemoval): limpa e re-adiciona na ordem
     * enviada. Nunca troca a referência da coleção (senão o Hibernate perde o rastreio dos órfãos).
     * Linhas sem pergunta ou sem resposta são ignoradas.
     */
    private static void aplicarFaq(Unidade unidade, UnidadeRequest req) {
        unidade.getFaq().clear();
        if (req.faq() == null) {
            return;
        }
        int ordem = 0;
        for (UnidadeRequest.FaqItem item : req.faq()) {
            if (item == null) {
                continue;
            }
            String pergunta = item.pergunta() == null ? "" : item.pergunta().trim();
            String resposta = item.resposta() == null ? "" : item.resposta().trim();
            if (pergunta.isBlank() || resposta.isBlank()) {
                continue;
            }
            UnidadeFaq faq = new UnidadeFaq();
            faq.setUnidade(unidade);
            faq.setPergunta(pergunta);
            faq.setResposta(resposta);
            faq.setOrdem(ordem++);
            unidade.getFaq().add(faq);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
