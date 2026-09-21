package com.example.pop.usoia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;

/** Consulta (só leitura) e exportação do ledger de uso de IA. */
@RestController
@RequestMapping("/uso-ia")
public class UsoIaController {

    private static final int TAMANHO_MAXIMO = 100;

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "criadoEm", "criadoEm",
            "tipo", "tipo",
            "descricao", "descricao",
            "custoUsd", "custoUsd",
            "tokensEntrada", "tokensEntrada",
            "tokensSaida", "tokensSaida");
    /** Ordenação padrão: mais recentes primeiro. */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by(Sort.Direction.DESC, "id"));

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UsoIaRepository repository;
    private final ExportacaoService exportacaoService;

    public UsoIaController(UsoIaRepository repository, ExportacaoService exportacaoService) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    public Pagina<UsoIaResponse> listar(
            @RequestParam(required = false) UsoIaTipo tipo,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<UsoIa> resultado = repository.search(tipo, inicio(de), fim(ate), texto(busca), pageable);
        List<UsoIaResponse> content = resultado.getContent().stream().map(UsoIaResponse::from).toList();
        return new Pagina<>(content, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast());
    }

    /** Totais (custo/tokens) do MESMO filtro — para bater com a fatura. */
    @GetMapping("/totais")
    public UsoIaTotaisResponse totais(
            @RequestParam(required = false) UsoIaTipo tipo,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            @RequestParam(required = false) String busca) {
        return UsoIaTotaisResponse.from(repository.somar(tipo, inicio(de), fim(ate), texto(busca)));
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) UsoIaTipo tipo,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(required = false) List<String> colunas) {
        List<UsoIa> dados = repository.search(tipo, inicio(de), fim(ate), texto(busca),
                Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO))).getContent();
        List<ColunaExport<UsoIa>> cols = ExportacaoService.filtrar(colunasUso(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Uso de IA", filtros(tipo, de, ate, busca), cols, dados)
                : exportacaoService.excel("Uso de IA", cols, dados);
        String nome = "uso-ia-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(arquivo);
    }

    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasUso().stream().map(ColunaExport::titulo).toList();
    }

    private List<FiltroAplicado> filtros(UsoIaTipo tipo, LocalDate de, LocalDate ate, String busca) {
        return List.of(
                new FiltroAplicado("Frente", tipo == null ? "Todas" : tipo.getDescricao()),
                new FiltroAplicado("De", de == null ? "—" : de.toString()),
                new FiltroAplicado("Até", ate == null ? "—" : ate.toString()),
                new FiltroAplicado("Busca", busca == null || busca.isBlank() ? "—" : busca));
    }

    private static List<ColunaExport<UsoIa>> colunasUso() {
        return List.of(
                ColunaExport.de("Data", u -> u.getCriadoEm() == null ? "" : u.getCriadoEm().format(DATA_HORA)),
                ColunaExport.de("Frente", u -> u.getTipo() == null ? "" : u.getTipo().getDescricao()),
                ColunaExport.de("Descrição", UsoIa::getDescricao),
                ColunaExport.de("Modelo", u -> texto(u.getModeloIa())),
                ColunaExport.de("Tokens entrada", u -> u.getTokensEntrada() == null ? "" : String.valueOf(u.getTokensEntrada())),
                ColunaExport.de("Tokens saída", u -> u.getTokensSaida() == null ? "" : String.valueOf(u.getTokensSaida())),
                ColunaExport.de("Custo (US$)", u -> u.getCustoUsd() == null ? "" : u.getCustoUsd().toPlainString()));
    }

    /** Texto nunca-nulo (trim): serve para a exportação e para a busca (a query usa {@code :busca = ''}). */
    private static String texto(String v) {
        return v == null ? "" : v.trim();
    }

    private static LocalDateTime inicio(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    private static LocalDateTime fim(LocalDate d) {
        return d == null ? null : d.atTime(LocalTime.MAX);
    }
}
