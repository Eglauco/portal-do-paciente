package com.example.pop.paciente;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final PacienteLogService logService;
    private final ResponsavelLancamentoService lancamentoService;
    private final com.example.pop.unidade.UnidadeRepository unidadeRepository;

    public PacienteController(PacienteRepository repository, PacienteAcessoService acessoService,
            ExportacaoService exportacaoService, StorageService storageService, PacienteLogService logService,
            ResponsavelLancamentoService lancamentoService,
            com.example.pop.unidade.UnidadeRepository unidadeRepository) {
        this.repository = repository;
        this.acessoService = acessoService;
        this.exportacaoService = exportacaoService;
        this.storageService = storageService;
        this.logService = logService;
        this.lancamentoService = lancamentoService;
        this.unidadeRepository = unidadeRepository;
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
            @RequestParam(required = false) String situacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int tamanho = Math.min(Math.max(size, 1), TAMANHO_MAXIMO);
        int pagina = Math.max(page, 0);
        String filtroNome = (nome == null) ? "" : nome.trim();
        String filtroCpf = digitos(cpf);
        String filtroProntuario = (prontuario == null) ? "" : prontuario.trim();

        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.ASC, "id"));
        Page<Paciente> resultado = repository.search(codigo, situacaoFiltro(situacao), filtroNome, filtroCpf, filtroProntuario, pageable);
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
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) List<String> colunas) {
        String filtroNome = (nome == null) ? "" : nome.trim();
        String filtroCpf = digitos(cpf);
        String filtroProntuario = (prontuario == null) ? "" : prontuario.trim();
        List<Paciente> dados = repository.search(codigo, situacaoFiltro(situacao), filtroNome, filtroCpf, filtroProntuario, Pageable.unpaged())
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
                ColunaExport.de("Situação do cadastro",
                        p -> p.getSituacao() == SituacaoCadastro.INATIVO ? "Inativo" : "Ativo"));
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

    /**
     * Filtro de situação da busca: ausente/"ATIVO" → só ativos (padrão, também herdado
     * por todos os seletores de paciente do sistema); "INATIVO" → só inativos; qualquer
     * outro valor (ex.: "TODOS") → sem filtro.
     */
    private static SituacaoCadastro situacaoFiltro(String valor) {
        if (valor == null || valor.isBlank() || "ATIVO".equalsIgnoreCase(valor)) {
            return SituacaoCadastro.ATIVO;
        }
        if ("INATIVO".equalsIgnoreCase(valor)) {
            return SituacaoCadastro.INATIVO;
        }
        return null; // TODOS
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
                    // Marca quais responsáveis têm lançamentos (o front bloqueia a remoção deles).
                    p.getResponsaveis().forEach(r -> r.setTemLancamentos(lancamentoService.temLancamentos(r.getId())));
                    return ResponseEntity.ok(p);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Linha do tempo de auditoria (LGPD) do cadastro: quem criou/alterou/inativou e o quê. */
    @GetMapping("/{id}/logs")
    public ResponseEntity<List<PacienteLogResponse>> logs(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logService.listar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Paciente criar(@Valid @RequestBody PacienteRequest dados, @AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = new Paciente();
        aplicar(paciente, dados);
        paciente.setAtivo(false); // a liberação é feita depois, via "gerar código"
        validarUnicidade(paciente, null);
        Paciente salvo = salvarUnico(paciente);
        // Auditoria (LGPD): quem cadastrou e quais campos preencheu. Na mesma transação:
        // se o log falhar, o cadastro inteiro é desfeito (não há alteração sem trilha).
        logService.registrarCriacao(salvo, uidDoToken(jwt));
        return salvo;
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Paciente> atualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest dados,
            @AuthenticationPrincipal Jwt jwt) {
        return repository.findById(id)
                .map(existente -> {
                    if (existente.getSituacao() == SituacaoCadastro.INATIVO) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Paciente inativo é somente leitura. Reative o cadastro para editar.");
                    }
                    // Fotografa o estado ANTES de aplicar as mudanças (para o diff granular).
                    PacienteLogService.SnapshotPaciente antes = logService.snapshot(existente);
                    aplicar(existente, dados);
                    // ativo/código/aparelho são geridos por gerar-codigo/revogar, não pelo corpo.
                    validarUnicidade(existente, id);
                    Paciente salvo = salvarUnico(existente);
                    logService.registrarAlteracao(antes, salvo, uidDoToken(jwt));
                    return ResponseEntity.ok(salvo);
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
        aplicarResponsaveis(p, r.responsaveis());
        aplicarUnidades(p, r.unidadeIds());

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

    /**
     * Reconcilia os responsáveis (cadastro paralelo) na coleção do paciente: atualiza os
     * existentes por id, cria os novos (id nulo) e remove os que saíram (orphanRemoval).
     * Muta a coleção no lugar — nunca substitui a instância (exigência do orphanRemoval).
     */
    /**
     * Integridade dos telefones dos responsáveis (mesma regra do app): nenhum pode repetir o
     * telefone de outro responsável do paciente, nem coincidir com um telefone do próprio
     * paciente (principal ou adicional) — o responsável tem de ser outra pessoa.
     */
    private void validarTelefonesDosResponsaveis(Paciente p, List<PacienteRequest.ResponsavelRequest> entradas) {
        Set<String> vistos = new HashSet<>();
        for (PacienteRequest.ResponsavelRequest entrada : entradas) {
            if (limpar(entrada.nome()) == null) {
                continue; // linha em branco: não vira responsável
            }
            String telefone = Documentos.somenteDigitos(entrada.telefone());
            if (telefone == null || telefone.isBlank()) {
                continue; // telefone do responsável é opcional no back-office
            }
            if (ResponsavelTelefones.ehDoPaciente(p, telefone)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "O responsável precisa ter um telefone diferente do paciente.");
            }
            if (!vistos.add(telefone)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Há mais de um responsável com o mesmo telefone.");
            }
        }
    }

    private void aplicarResponsaveis(Paciente p, List<PacienteRequest.ResponsavelRequest> reqs) {
        List<PacienteRequest.ResponsavelRequest> entradas = reqs == null ? List.of() : reqs;
        validarTelefonesDosResponsaveis(p, entradas);

        Map<Long, Responsavel> existentes = new HashMap<>();
        for (Responsavel r : p.getResponsaveis()) {
            if (r.getId() != null) {
                existentes.put(r.getId(), r);
            }
        }
        Set<Long> mantidos = new HashSet<>();

        for (PacienteRequest.ResponsavelRequest entrada : entradas) {
            String nome = limpar(entrada.nome());
            if (nome == null) {
                continue; // ignora linhas em branco
            }
            String telefone = Documentos.somenteDigitos(entrada.telefone());
            Responsavel alvo = entrada.id() == null ? null : existentes.get(entrada.id());
            if (alvo != null) {
                alvo.setNome(nome);
                alvo.setTelefone(telefone);
                alvo.setDataNascimento(entrada.dataNascimento());
                alvo.setAtivo(entrada.ativoOuPadrao());
                aplicarPermissoes(alvo, entrada.permissoes());
                mantidos.add(alvo.getId());
            } else {
                Responsavel novo = new Responsavel();
                novo.setNome(nome);
                novo.setTelefone(telefone);
                novo.setDataNascimento(entrada.dataNascimento());
                novo.setAtivo(entrada.ativoOuPadrao());
                novo.setPaciente(p);
                aplicarPermissoes(novo, entrada.permissoes());
                p.getResponsaveis().add(novo);
            }
        }
        // Bloqueia a remoção de responsável com lançamentos (preserva a autoria): só resta
        // inativar. Checa ANTES do removeIf; se disparar, a transação inteira é desfeita.
        for (Responsavel r : p.getResponsaveis()) {
            if (r.getId() != null && !mantidos.contains(r.getId()) && lancamentoService.temLancamentos(r.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Não é possível remover o responsável \"" + r.getNome()
                                + "\": há lançamentos dele no sistema. Inative-o para preservar o histórico.");
            }
        }
        // Remove os existentes (sem lançamentos) que não vieram no request (orphanRemoval apaga).
        p.getResponsaveis().removeIf(r -> r.getId() != null && !mantidos.contains(r.getId()));
    }

    /**
     * Substitui as permissões do responsável pelas do request. Só grava as concessões
     * (nível diferente de SEM_ACESSO); as demais funcionalidades ficam ausentes = sem
     * acesso. Muta o mapa no lugar (não troca a instância — exigência do @ElementCollection).
     */
    private static void aplicarPermissoes(Responsavel alvo,
            Map<FuncionalidadeApp, NivelAcessoResponsavel> permissoes) {
        Map<FuncionalidadeApp, NivelAcessoResponsavel> destino = alvo.getPermissoes();
        destino.clear();
        if (permissoes != null) {
            permissoes.forEach((func, nivel) -> {
                if (func != null && nivel != null && nivel != NivelAcessoResponsavel.SEM_ACESSO) {
                    destino.put(func, nivel);
                }
            });
        }
    }

    /** Reconcilia as unidades de acesso do paciente pelos ids do request (muta a coleção no lugar). */
    private void aplicarUnidades(Paciente p, List<Long> unidadeIds) {
        Set<Long> ids = unidadeIds == null ? Set.of()
                : unidadeIds.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        p.getUnidades().clear();
        if (!ids.isEmpty()) {
            p.getUnidades().addAll(unidadeRepository.findAllById(ids));
        }
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

    /**
     * Inativa o cadastro (soft-delete): fica somente-leitura e some dos seletores/pesquisas.
     * Também revoga o acesso ao app (desliga o "liberado" e desloga o aparelho). Idempotente.
     */
    @PostMapping("/{id}/inativar")
    @Transactional
    public ResponseEntity<Void> inativar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = repository.findById(id).orElse(null);
        if (paciente == null) {
            return ResponseEntity.notFound().build();
        }
        if (paciente.getSituacao() == SituacaoCadastro.INATIVO) {
            return ResponseEntity.noContent().build();
        }
        paciente.setSituacao(SituacaoCadastro.INATIVO);
        acessoService.revogar(paciente); // desliga app + desloga; também persiste o paciente
        logService.registrarInativacao(paciente, uidDoToken(jwt));
        return ResponseEntity.noContent().build();
    }

    /**
     * Reativa o cadastro (volta ATIVO). Não relibera o acesso ao app sozinho — isso é
     * feito de novo por "gerar código". Idempotente.
     */
    @PostMapping("/{id}/reativar")
    @Transactional
    public ResponseEntity<Void> reativar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = repository.findById(id).orElse(null);
        if (paciente == null) {
            return ResponseEntity.notFound().build();
        }
        if (paciente.getSituacao() == SituacaoCadastro.ATIVO) {
            return ResponseEntity.noContent().build();
        }
        paciente.setSituacao(SituacaoCadastro.ATIVO);
        repository.save(paciente);
        logService.registrarReativacao(paciente, uidDoToken(jwt));
        return ResponseEntity.noContent().build();
    }

    /** uid do atendente logado (nulo se não houver token — ex.: chamadas diretas em teste). */
    private static Long uidDoToken(Jwt jwt) {
        return jwt != null && jwt.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
    }

    private Paciente salvarUnico(Paciente paciente) {
        try {
            // saveAndFlush: dentro da transação de criar/atualizar, força a checagem de
            // unicidade a aflorar AQUI (e virar 409) em vez de só no commit (viraria 500).
            return repository.saveAndFlush(paciente);
        } catch (DataIntegrityViolationException e) {
            // Rede de segurança para corridas: os campos únicos já são checados antes.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um paciente com um dos dados únicos (telefone, CPF, CNS, código de integração ou prontuário)");
        }
    }
}
