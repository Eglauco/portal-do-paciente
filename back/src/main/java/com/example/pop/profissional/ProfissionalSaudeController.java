package com.example.pop.profissional;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.agendamento.AgendamentoRepository;
import com.example.pop.common.Ordenacoes;
import com.example.pop.common.Pagina;
import com.example.pop.conselho.ConselhoRepository;
import com.example.pop.especialidade.Especialidade;
import com.example.pop.especialidade.EspecialidadeRepository;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.paciente.Documentos;
import com.example.pop.storage.StorageService;
import com.example.pop.unidade.Unidade;
import com.example.pop.unidade.UnidadeRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/profissional")
public class ProfissionalSaudeController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;
    /** Pasta no S3 onde ficam as fotos dos profissionais. */
    private static final String PASTA_FOTO = "foto-profissional";
    /** Validade da URL de visualização da foto. */
    private static final Duration FOTO_TTL = Duration.ofHours(6);

    /** Colunas ordenáveis da tela → propriedade da entidade (whitelist da ordenação). */
    private static final Map<String, String> ORDENAVEIS = Map.of(
            "codigo", "id",
            "nome", "nome");
    /** Ordenação usada quando nada é escolhido na tela. */
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.ASC, "nome", "id");

    private final ProfissionalSaudeRepository repository;
    private final ExportacaoService exportacaoService;
    private final StorageService storageService;
    private final ConselhoRepository conselhoRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final UnidadeRepository unidadeRepository;
    private final AgendamentoRepository agendamentoRepository;

    public ProfissionalSaudeController(ProfissionalSaudeRepository repository, ExportacaoService exportacaoService,
            StorageService storageService, ConselhoRepository conselhoRepository,
            EspecialidadeRepository especialidadeRepository, UnidadeRepository unidadeRepository,
            AgendamentoRepository agendamentoRepository) {
        this.repository = repository;
        this.exportacaoService = exportacaoService;
        this.storageService = storageService;
        this.conselhoRepository = conselhoRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.unidadeRepository = unidadeRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

    /**
     * Lista profissionais de forma paginada. Por padrão só os ATIVOS (para as seleções de
     * outras telas verem apenas ativos); o back-office pode pedir INATIVO/TODOS.
     */
    @GetMapping
    public Pagina<ProfissionalSaude> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false, defaultValue = "ATIVO") String situacao,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO));
        Page<ProfissionalSaude> resultado = repository.search(codigo, filtroNome, ativoFiltro(situacao), pageable);
        // Foto da lista: troca a URL crua pela GET pré-assinada (só p/ exibição).
        resultado.getContent().forEach(p -> p.setFotoUrl(storageService.urlVisualizacao(p.getFotoUrl(), FOTO_TTL)));

        return new Pagina<>(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    /** Situação da tela → filtro de ativo: INATIVO=false, TODOS=null, ATIVO(padrão)=true. */
    private static Boolean ativoFiltro(String situacao) {
        if ("TODOS".equalsIgnoreCase(situacao)) {
            return null;
        }
        return !"INATIVO".equalsIgnoreCase(situacao);
    }

    /**
     * Exporta os profissionais que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenados por nome.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false, defaultValue = "ATIVO") String situacao,
            @RequestParam(required = false) List<String> ordenar,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();
        List<ProfissionalSaude> dados = repository.search(codigo, filtroNome, ativoFiltro(situacao),
                Pageable.unpaged(Ordenacoes.montar(ordenar, ORDENAVEIS, ORDEM_PADRAO))).getContent();
        List<ColunaExport<ProfissionalSaude>> cols = ExportacaoService.filtrar(colunasProfissional(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Profissionais", filtrosProfissional(codigo, filtroNome, situacao), cols, dados)
                : exportacaoService.excel("Profissionais", cols, dados);
        String nomeArquivo = "profissionais-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasProfissional().stream().map(ColunaExport::titulo).toList();
    }

    private List<FiltroAplicado> filtrosProfissional(Long codigo, String nome, String situacao) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", (nome != null && !nome.isBlank()) ? nome : "Todos"),
                new FiltroAplicado("Situação", situacaoRotulo(situacao)));
    }

    private static String situacaoRotulo(String situacao) {
        if ("TODOS".equalsIgnoreCase(situacao)) {
            return "Todos";
        }
        return "INATIVO".equalsIgnoreCase(situacao) ? "Inativos" : "Ativos";
    }

    private static List<ColunaExport<ProfissionalSaude>> colunasProfissional() {
        return List.of(
                ColunaExport.de("Código", p -> p.getId() == null ? "" : String.valueOf(p.getId())),
                ColunaExport.de("Nome", ProfissionalSaude::getNome),
                ColunaExport.de("Conselho", p -> p.getConselho() == null ? "" : p.getConselho().getSigla()),
                ColunaExport.de("Nº do conselho", p -> texto(p.getNumeroConselho())),
                ColunaExport.de("CPF", p -> formatarCpf(p.getCpf())),
                ColunaExport.de("CNS", p -> texto(p.getCns())),
                ColunaExport.de("Telefone", p -> texto(p.getTelefone())),
                ColunaExport.de("E-mail", p -> texto(p.getEmail())),
                ColunaExport.de("Situação", p -> p.isAtivo() ? "Ativo" : "Inativo"),
                ColunaExport.de("Especialidades", p -> p.getEspecialidades().stream()
                        .map(Especialidade::getNome).collect(Collectors.joining("; "))),
                ColunaExport.de("Unidades", p -> p.getUnidades().stream()
                        .map(Unidade::getNome).collect(Collectors.joining("; "))),
                ColunaExport.de("Cód. integração", p -> texto(p.getCodigoIntegracao())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfissionalSaude> buscar(@PathVariable Long id) {
        // Devolve a fotoUrl CRUA (não pré-assinada): o form a reenvia inalterada ao salvar e
        // resolve a URL de exibição à parte (via /storage/download-url). Só a lista pré-assina.
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfissionalSaude criar(@Valid @RequestBody ProfissionalSaudeRequest request) {
        ProfissionalSaude profissional = new ProfissionalSaude();
        aplicar(profissional, request);
        validarUnicidade(profissional, null);
        return salvarUnico(profissional);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfissionalSaude> atualizar(@PathVariable Long id,
            @Valid @RequestBody ProfissionalSaudeRequest request) {
        return repository.findById(id)
                .map(existente -> {
                    aplicar(existente, request);
                    validarUnicidade(existente, id);
                    return ResponseEntity.ok(salvarUnico(existente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Copia o request para a entidade, normalizando dígitos e validando CPF/CNS. */
    private void aplicar(ProfissionalSaude p, ProfissionalSaudeRequest r) {
        p.setNome(r.nome().trim());
        p.setConselho(r.conselhoId() == null ? null : conselhoRepository.findById(r.conselhoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conselho não encontrado")));
        p.setNumeroConselho(limpar(r.numeroConselho()));
        p.setSexo(r.sexo());
        p.setDataNascimento(r.dataNascimento());
        p.setRg(limpar(r.rg()));
        p.setCpf(Documentos.somenteDigitos(r.cpf()));
        p.setCns(Documentos.somenteDigitos(r.cns()));
        p.setTelefone(Documentos.somenteDigitos(r.telefone()));
        p.setTelefonesAdicionais(normalizarTelefones(r.telefonesAdicionais()));
        p.setRua(limpar(r.rua()));
        p.setNumero(limpar(r.numero()));
        p.setBairro(limpar(r.bairro()));
        p.setMunicipio(limpar(r.municipio()));
        p.setUf(limparUf(r.uf()));
        p.setCep(Documentos.somenteDigitos(r.cep()));
        p.setComplemento(limpar(r.complemento()));
        p.setEmail(limparEmail(r.email()));
        p.setFotoUrl(limpar(r.fotoUrl()));
        p.setCodigoIntegracao(limpar(r.codigoIntegracao()));
        aplicarEspecialidades(p, r.especialidadeIds());
        aplicarUnidades(p, r.unidadeIds());
        validarDocumentos(p);
    }

    private static void validarDocumentos(ProfissionalSaude p) {
        if (p.getCpf() != null && !Documentos.cpfValido(p.getCpf())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF inválido");
        }
        if (p.getCns() != null && !Documentos.cnsValido(p.getCns())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNS inválido");
        }
    }

    /** Bloqueia duplicidade nos campos únicos (ignorando o próprio registro na edição). */
    private void validarUnicidade(ProfissionalSaude p, Long idAtual) {
        Long id = (idAtual == null) ? -1L : idAtual;
        if (p.getCpf() != null && repository.existsByCpfAndIdNot(p.getCpf(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um profissional com este CPF");
        }
        if (p.getCns() != null && repository.existsByCnsAndIdNot(p.getCns(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um profissional com este CNS");
        }
        if (p.getCodigoIntegracao() != null && repository.existsByCodigoIntegracaoAndIdNot(p.getCodigoIntegracao(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um profissional com este código de integração");
        }
    }

    private ProfissionalSaude salvarUnico(ProfissionalSaude profissional) {
        try {
            return repository.saveAndFlush(profissional);
        } catch (DataIntegrityViolationException e) {
            // Só uma violação de UNIQUE (SQLState 23505) é conflito de dado único (409);
            // outras (ex.: tamanho de campo estourado) são dados inválidos (400).
            if (e.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Já existe um profissional com um dos dados únicos (CPF, CNS ou código de integração)");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dados inválidos: verifique o tamanho dos campos (ex.: CEP com 8 dígitos, telefone com até 20).");
        }
    }

    private void aplicarEspecialidades(ProfissionalSaude p, List<Long> ids) {
        p.getEspecialidades().clear();
        List<Long> limpos = idsLimpos(ids);
        if (!limpos.isEmpty()) {
            p.getEspecialidades().addAll(especialidadeRepository.findAllById(limpos));
        }
    }

    private void aplicarUnidades(ProfissionalSaude p, List<Long> ids) {
        p.getUnidades().clear();
        List<Long> limpos = idsLimpos(ids);
        if (!limpos.isEmpty()) {
            p.getUnidades().addAll(unidadeRepository.findAllById(limpos));
        }
    }

    private static List<Long> idsLimpos(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /** Telefones adicionais: só dígitos, sem vazios nem repetidos. */
    private static List<String> normalizarTelefones(List<String> brutos) {
        if (brutos == null) {
            return new ArrayList<>();
        }
        return brutos.stream()
                .map(Documentos::somenteDigitos)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    private static String limparUf(String uf) {
        String t = limpar(uf);
        return t == null ? null : t.toUpperCase();
    }

    private static String limparEmail(String email) {
        String t = limpar(email);
        return t == null ? null : t.toLowerCase();
    }

    /**
     * Inativa o profissional: some das seleções (agendamento etc.) e das regras de negócio,
     * marcando a data/hora. Idempotente.
     */
    @PostMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        ProfissionalSaude p = repository.findById(id).orElse(null);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        if (!p.isAtivo()) {
            return ResponseEntity.noContent().build();
        }
        p.setAtivo(false);
        p.setInativadoEm(LocalDateTime.now());
        repository.save(p);
        return ResponseEntity.noContent().build();
    }

    /** Reativa o profissional (volta a aparecer nas seleções). Idempotente. */
    @PostMapping("/{id}/reativar")
    public ResponseEntity<Void> reativar(@PathVariable Long id) {
        ProfissionalSaude p = repository.findById(id).orElse(null);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        if (p.isAtivo()) {
            return ResponseEntity.noContent().build();
        }
        p.setAtivo(true);
        p.setInativadoEm(null);
        repository.save(p);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // Com lançamentos (agendamentos, e por tabela os prontuários), NÃO exclui: preserva o
        // histórico e a auditoria. O caminho é inativar.
        if (agendamentoRepository.existsByProfissionalSaude_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Profissional com lançamentos (agendamentos/prontuários). Inative-o para preservar o histórico.");
        }
        try {
            repository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Profissional em uso. Inative-o em vez de excluir.");
        }
        return ResponseEntity.noContent().build();
    }

    private static String texto(String v) {
        return v == null ? "" : v;
    }

    private static String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return cpf == null ? "" : cpf;
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }
}
