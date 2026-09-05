package com.example.pop.paciente;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

import com.example.pop.common.Pagina;
import com.example.pop.export.ColunaExport;
import com.example.pop.export.ExportacaoService;
import com.example.pop.export.FiltroAplicado;
import com.example.pop.storage.StorageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/paciente")
public class PacienteController {

    /** Máximo de registros retornados por página. */
    private static final int TAMANHO_MAXIMO = 100;

    private final PacienteRepository repository;
    private final PacienteAcessoService acessoService;
    private final ExportacaoService exportacaoService;
    private final StorageService storageService;

    public PacienteController(PacienteRepository repository, PacienteAcessoService acessoService,
            ExportacaoService exportacaoService, StorageService storageService) {
        this.repository = repository;
        this.acessoService = acessoService;
        this.exportacaoService = exportacaoService;
        this.storageService = storageService;
    }

    /**
     * Lista pacientes de forma paginada, com filtros opcionais por código e nome.
     * O tamanho da página é limitado a {@value #TAMANHO_MAXIMO} registros.
     */
    @GetMapping
    public Pagina<Paciente> listar(
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String prontuario,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();
        String filtroCpf = digitos(cpf);
        String filtroProntuario = (prontuario == null) ? "" : prontuario.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<Paciente> resultado = repository.search(codigo, filtroNome, filtroCpf, filtroProntuario, pageable);
        // Avatar da lista: troca a URL crua da foto pela GET pré-assinada (só p/ exibição;
        // as entidades já estão destacadas fora de transação, então não persiste nada).
        resultado.getContent().forEach(p -> p.setFotoUrl(storageService.urlFotoPaciente(p.getFotoUrl())));

        return new Pagina<>(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast());
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Exporta os pacientes que batem com os MESMOS filtros da tela (todos os
     * registros, sem paginação) em Excel (padrão) ou PDF. Ordenados por código.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) Long codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String prontuario,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();
        String filtroCpf = digitos(cpf);
        String filtroProntuario = (prontuario == null) ? "" : prontuario.trim();
        List<Paciente> dados = repository.search(codigo, filtroNome, filtroCpf, filtroProntuario, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Paciente::getId))
                .toList();
        List<ColunaExport<Paciente>> cols = ExportacaoService.filtrar(colunasPaciente(), colunas);

        boolean pdf = "pdf".equalsIgnoreCase(formato);
        byte[] arquivo = pdf
                ? exportacaoService.pdf("Pacientes", filtrosPaciente(codigo, nome, cpf, prontuario), cols, dados)
                : exportacaoService.excel("Pacientes", cols, dados);
        String nomeArquivo = "pacientes-" + LocalDate.now() + (pdf ? ".pdf" : ".xlsx");

        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(ExportacaoService.TIPO_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(arquivo);
    }

    /** Rótulos de todas as colunas disponíveis do relatório (para o modal de seleção). */
    @GetMapping("/exportar/colunas")
    public List<String> colunasDisponiveis() {
        return colunasPaciente().stream().map(ColunaExport::titulo).toList();
    }

    /** Filtros aplicados (mesmos da tela) para o cabeçalho do PDF — mostra o que estava ativo. */
    private List<FiltroAplicado> filtrosPaciente(Long codigo, String nome, String cpf, String prontuario) {
        return List.of(
                new FiltroAplicado("Código", codigo != null ? String.valueOf(codigo) : "Todos"),
                new FiltroAplicado("Nome", (nome != null && !nome.isBlank()) ? nome.trim() : "Todos"),
                new FiltroAplicado("CPF", (cpf != null && !cpf.isBlank()) ? formatarCpf(digitos(cpf)) : "Todos"),
                new FiltroAplicado("Prontuário", (prontuario != null && !prontuario.isBlank()) ? prontuario.trim() : "Todos"));
    }

    /** Todas as colunas disponíveis do paciente (o usuário escolhe quais exportar). */
    private static List<ColunaExport<Paciente>> colunasPaciente() {
        return List.of(
                ColunaExport.de("Código", p -> String.valueOf(p.getId())),
                ColunaExport.de("Nome", Paciente::getNome),
                ColunaExport.de("Sexo", p -> sexoLabel(p.getSexo())),
                ColunaExport.de("Data de nascimento", p -> p.getDataNascimento() == null ? "" : p.getDataNascimento().format(DATA)),
                ColunaExport.de("RG", p -> texto(p.getRg())),
                ColunaExport.de("CPF", p -> formatarCpf(p.getCpf())),
                ColunaExport.de("CNS", p -> texto(p.getCns())),
                ColunaExport.de("Nome da mãe", p -> texto(p.getNomeMae())),
                ColunaExport.de("Nome do pai", p -> texto(p.getNomePai())),
                ColunaExport.de("Cód. integração", p -> texto(p.getCodigoIntegracao())),
                ColunaExport.de("Prontuário", p -> texto(p.getProntuario())),
                ColunaExport.de("Telefone", p -> formatarTelefone(p.getTelefone())),
                ColunaExport.de("Telefones adicionais", p -> p.getTelefonesAdicionais() == null ? ""
                        : p.getTelefonesAdicionais().stream().map(PacienteController::formatarTelefone)
                                .collect(java.util.stream.Collectors.joining("; "))),
                ColunaExport.de("E-mail", p -> texto(p.getEmail())),
                ColunaExport.de("Rua", p -> texto(p.getRua())),
                ColunaExport.de("Número", p -> texto(p.getNumero())),
                ColunaExport.de("Bairro", p -> texto(p.getBairro())),
                ColunaExport.de("Município", p -> texto(p.getMunicipio())),
                ColunaExport.de("UF", p -> texto(p.getUf())),
                ColunaExport.de("CEP", p -> formatarCep(p.getCep())),
                ColunaExport.de("Complemento", p -> texto(p.getComplemento())),
                ColunaExport.de("Liberado (app)", p -> p.isAtivo() ? "Sim" : "Não"),
                ColunaExport.de("Usando o app", p -> p.getDispositivoAtivo() != null ? "Sim" : "Não"),
                ColunaExport.de("Código expira em",
                        p -> p.getCodigoAtivacaoExpiraEm() == null ? "" : p.getCodigoAtivacaoExpiraEm().format(DATA_HORA)));
    }

    private static String texto(String v) {
        return v == null ? "" : v;
    }

    private static String sexoLabel(Sexo s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case MASCULINO -> "Masculino";
            case FEMININO -> "Feminino";
            case OUTRO -> "Outro";
            case NAO_INFORMADO -> "Não informado";
        };
    }

    /** Formata o CEP (só dígitos) como 00000-000; devolve o valor original se não tiver 8 dígitos. */
    private static String formatarCep(String cep) {
        String d = cep == null ? "" : cep.replaceAll("\\D", "");
        return d.length() == 8 ? d.substring(0, 5) + "-" + d.substring(5) : texto(cep);
    }

    /** Formata o CPF (só dígitos) como 000.000.000-00; devolve vazio se não tiver 11 dígitos. */
    private static String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return cpf == null ? "" : cpf;
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    /** Só os dígitos de um filtro (ou "" quando vazio). */
    private static String digitos(String valor) {
        String d = Documentos.somenteDigitos(valor);
        return d == null ? "" : d;
    }

    /** Formata o telefone (só dígitos) no padrão brasileiro; devolve o valor original se não reconhecer. */
    private static String formatarTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return "";
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 7) + "-" + digitos.substring(7);
        }
        if (digitos.length() == 10) {
            return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 6) + "-" + digitos.substring(6);
        }
        return telefone;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Paciente> buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(p -> {
                    // Foto só p/ exibição no form (avatar): URL crua → GET pré-assinada.
                    // aplicar()/PacienteRequest não tocam fotoUrl, então salvar não persiste a assinada.
                    p.setFotoUrl(storageService.urlFotoPaciente(p.getFotoUrl()));
                    return ResponseEntity.ok(p);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Paciente criar(@Valid @RequestBody PacienteRequest dados) {
        Paciente paciente = new Paciente();
        aplicar(paciente, dados);
        paciente.setAtivo(false); // a liberação é feita depois, via "gerar código"
        validarUnicidade(paciente, null);
        return salvarUnico(paciente);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Paciente> atualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest dados) {
        return repository.findById(id)
                .map(existente -> {
                    aplicar(existente, dados);
                    // ativo/código/aparelho são geridos por gerar-codigo/revogar, não pelo corpo.
                    validarUnicidade(existente, id);
                    return ResponseEntity.ok(salvarUnico(existente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Copia o request para a entidade, normalizando dígitos e validando CPF/CNS. */
    private void aplicar(Paciente p, PacienteRequest r) {
        p.setNome(r.nome().trim());
        p.setTelefone(Documentos.somenteDigitos(r.telefone()));
        p.setCodigoIntegracao(limpar(r.codigoIntegracao()));
        p.setProntuario(limpar(r.prontuario()));
        p.setSexo(r.sexo());
        p.setDataNascimento(r.dataNascimento());
        p.setRg(limpar(r.rg()));
        p.setCpf(Documentos.somenteDigitos(r.cpf()));
        p.setNomeMae(limpar(r.nomeMae()));
        p.setNomePai(limpar(r.nomePai()));
        p.setRua(limpar(r.rua()));
        p.setNumero(limpar(r.numero()));
        p.setBairro(limpar(r.bairro()));
        p.setMunicipio(limpar(r.municipio()));
        p.setUf(limparUf(r.uf()));
        p.setCep(Documentos.somenteDigitos(r.cep()));
        p.setComplemento(limpar(r.complemento()));
        p.setEmail(limparEmail(r.email()));
        p.setCns(Documentos.somenteDigitos(r.cns()));
        p.setTelefonesAdicionais(normalizarTelefones(r.telefonesAdicionais()));

        if (p.getCpf() != null && !Documentos.cpfValido(p.getCpf())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF inválido");
        }
        if (p.getCns() != null && !Documentos.cnsValido(p.getCns())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNS inválido");
        }
    }

    /** Bloqueia duplicidade nos campos únicos (ignorando o próprio registro na edição). */
    private void validarUnicidade(Paciente p, Long idAtual) {
        Long id = (idAtual == null) ? -1L : idAtual;
        if (p.getTelefone() != null && repository.existsByTelefoneAndIdNot(p.getTelefone(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um paciente com este telefone");
        }
        if (p.getCpf() != null && repository.existsByCpfAndIdNot(p.getCpf(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um paciente com este CPF");
        }
        if (p.getCns() != null && repository.existsByCnsAndIdNot(p.getCns(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um paciente com este CNS");
        }
        if (p.getCodigoIntegracao() != null && repository.existsByCodigoIntegracaoAndIdNot(p.getCodigoIntegracao(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um paciente com este código de integração");
        }
        if (p.getProntuario() != null && repository.existsByProntuarioAndIdNot(p.getProntuario(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um paciente com este prontuário");
        }
    }

    /** Trim; null quando vazio (evita gravar "" e colidir nos índices únicos). */
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

    /** Telefones adicionais: só dígitos, sem vazios nem repetidos. */
    private static List<String> normalizarTelefones(List<String> brutos) {
        if (brutos == null) {
            return new ArrayList<>();
        }
        return brutos.stream()
                .map(Documentos::somenteDigitos)
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Revoga o acesso do paciente ao app (desativa e desloga o aparelho). */
    @PostMapping("/{id}/revogar-acesso")
    public ResponseEntity<Void> revogarAcesso(@PathVariable Long id) {
        Paciente paciente = repository.findById(id).orElse(null);
        if (paciente == null) {
            return ResponseEntity.notFound().build();
        }
        acessoService.revogar(paciente);
        return ResponseEntity.noContent().build();
    }

    private Paciente salvarUnico(Paciente paciente) {
        try {
            return repository.save(paciente);
        } catch (DataIntegrityViolationException e) {
            // Rede de segurança para corridas: os campos únicos já são checados antes.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um paciente com um dos dados únicos (telefone, CPF, CNS, código de integração ou prontuário)");
        }
    }
}
