package com.example.pop.paciente;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.storage.StorageService;
import com.example.pop.storage.UploadUrlResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Perfil do paciente logado (app): visualização SOMENTE-LEITURA dos dados do
 * cadastro + troca da foto (upload direto ao S3 na pasta "foto-paciente").
 * Sob /meu/** → papel PACIENTE; o paciente vem do token, nunca do cliente.
 */
@RestController
@RequestMapping("/meu/perfil")
public class MeuPerfilController {

    private static final String PASTA_FOTO = "foto-paciente";
    private static final Duration VALIDADE_FOTO = Duration.ofHours(1);

    private final PacienteRepository repository;
    private final PacienteAcessoService acessoService;
    private final StorageService storageService;
    private final PacienteLogService logService;

    public MeuPerfilController(PacienteRepository repository, PacienteAcessoService acessoService,
            StorageService storageService, PacienteLogService logService) {
        this.repository = repository;
        this.acessoService = acessoService;
        this.storageService = storageService;
        this.logService = logService;
    }

    /** Dados do paciente logado (somente leitura) + link temporário da foto. */
    @GetMapping
    public MeuPerfilResponse meuPerfil(@AuthenticationPrincipal Jwt jwt) {
        acessoService.exigirVisualizar(jwt, FuncionalidadeApp.MEU_PERFIL);
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        return MeuPerfilResponse.from(paciente, storageService.urlVisualizacao(paciente.getFotoUrl(), VALIDADE_FOTO));
    }

    /**
     * O paciente edita os PRÓPRIOS dados pessoais pelo app. Só campos pessoais/contato/
     * endereço (ver {@link MeuPerfilRequest}); CPF, código de integração, prontuário e
     * unidades NÃO mudam por aqui (identidade/administrativos, geridos pelo back-office).
     * Exige "Ver e lançar" em Meu Perfil (perfil próprio sempre; responsável só com permissão).
     * Auditado (LGPD) com autor PACIENTE ou RESPONSAVEL.
     */
    @PutMapping
    public MeuPerfilResponse atualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MeuPerfilRequest req) {
        acessoService.exigirLancar(jwt, FuncionalidadeApp.MEU_PERFIL);
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        PacienteLogService.SnapshotPaciente antes = logService.snapshot(paciente);
        aplicar(paciente, req);
        Paciente salvo = salvar(paciente);
        // Auditoria: RESPONSAVEL se a sessão age por um dependente; senão o próprio paciente.
        logService.registrarAlteracaoPeloApp(antes, salvo, acessoService.responsavelDaSessao(jwt).orElse(null));
        return MeuPerfilResponse.from(salvo, storageService.urlVisualizacao(salvo.getFotoUrl(), VALIDADE_FOTO));
    }

    /**
     * Altera/define o PIN de acesso da CONTA logada (para entrar sem SMS). É credencial da conta
     * (não dado do perfil), então não passa pela trava de MEU_PERFIL — vale para paciente e
     * responsável. Quando já existe senha, exige a senha atual.
     */
    @PostMapping("/senha")
    public void alterarSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AlterarSenhaRequest req) {
        ContaApp conta = acessoService.contaParaTroca(jwt); // valida a conta (cid) + aparelho
        acessoService.alterarSenha(conta.getId(), jwt.getClaimAsString("dev"), req.senhaAtual(), req.senhaNova());
    }

    /** URL pré-assinada (PUT) para o app enviar a foto direto ao S3 (pasta fixa "foto-paciente"). */
    @PostMapping("/foto/upload-url")
    public UploadUrlResponse gerarUploadFoto(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FotoUploadRequest req) {
        acessoService.exigirLancar(jwt, FuncionalidadeApp.MEU_PERFIL);
        acessoService.pacienteDoToken(jwt); // valida a sessão do paciente
        try {
            return storageService.gerarUploadUrl(req.nomeArquivo(), req.contentType(), PASTA_FOTO);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível preparar o envio da foto.");
        }
    }

    /**
     * Persiste a foto enviada. Valida que a URL é da pasta "foto-paciente" (o app
     * não pode gravar como foto a URL de outro objeto) e remove a foto anterior.
     */
    @PutMapping("/foto")
    public MeuPerfilResponse salvarFoto(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SalvarFotoRequest req) {
        acessoService.exigirLancar(jwt, FuncionalidadeApp.MEU_PERFIL);
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        if (!storageService.urlNaPasta(req.url(), PASTA_FOTO)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de foto inválida.");
        }
        String anterior = paciente.getFotoUrl();
        paciente.setFotoUrl(req.url());
        repository.save(paciente);
        if (anterior != null && !anterior.equals(req.url())) {
            try {
                storageService.excluirPorUrl(anterior);
            } catch (RuntimeException ignored) {
                // best-effort: falhar ao apagar a foto antiga não impede a troca
            }
        }
        return MeuPerfilResponse.from(paciente, storageService.urlVisualizacao(paciente.getFotoUrl(), VALIDADE_FOTO));
    }

    /** Remove a foto do paciente e apaga o objeto no S3 (best-effort). */
    @DeleteMapping("/foto")
    public MeuPerfilResponse removerFoto(@AuthenticationPrincipal Jwt jwt) {
        acessoService.exigirLancar(jwt, FuncionalidadeApp.MEU_PERFIL);
        Paciente paciente = acessoService.pacienteDoToken(jwt);
        String anterior = paciente.getFotoUrl();
        if (anterior != null) {
            paciente.setFotoUrl(null);
            repository.save(paciente);
            try {
                storageService.excluirPorUrl(anterior);
            } catch (RuntimeException ignored) {
                // best-effort: falha ao apagar no S3 não impede remover a referência
            }
        }
        return MeuPerfilResponse.from(paciente, null);
    }

    /** Copia os campos editáveis do request para a entidade, normalizando dígitos e validando. */
    private void aplicar(Paciente p, MeuPerfilRequest r) {
        p.setNome(r.nome().trim());
        p.setSexo(r.sexo());
        p.setDataNascimento(r.dataNascimento());
        p.setRg(limpar(r.rg()));
        p.setCns(Documentos.somenteDigitos(r.cns()));
        p.setNomeMae(limpar(r.nomeMae()));
        p.setNomePai(limpar(r.nomePai()));
        p.setEmail(limparEmail(r.email()));
        p.setRua(limpar(r.rua()));
        p.setNumero(limpar(r.numero()));
        p.setComplemento(limpar(r.complemento()));
        p.setBairro(limpar(r.bairro()));
        p.setMunicipio(limpar(r.municipio()));
        p.setUf(limparUf(r.uf()));
        p.setCep(Documentos.somenteDigitos(r.cep()));
        p.setTelefonesAdicionais(normalizarTelefones(r.telefonesAdicionais()));
        // NÃO altera: cpf, codigoIntegracao, prontuario, unidades, responsaveis, fotoUrl, ativo, situacao, dispositivo.

        if (p.getTelefonesAdicionais().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe pelo menos um telefone.");
        }
        if (p.getCns() != null && !Documentos.cnsValido(p.getCns())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNS inválido");
        }
        if (p.getCns() != null && repository.existsByCnsAndIdNot(p.getCns(), p.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um paciente com este CNS");
        }
    }

    /** saveAndFlush: força a checagem de unicidade a virar 409 aqui (não 500 no commit). */
    private Paciente salvar(Paciente paciente) {
        try {
            return repository.saveAndFlush(paciente);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um paciente com este CNS.");
        }
    }

    /** Trim; null quando vazio (evita gravar "" e colidir no índice único do CNS). */
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

    /** Telefones: só dígitos, sem vazios nem repetidos (mesma regra do back-office). */
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

    /** Nome do arquivo + tipo para assinar o upload (o app não escolhe a pasta). */
    public record FotoUploadRequest(@NotBlank String nomeArquivo, String contentType) {
    }

    /** URL pública do objeto recém-enviado ao S3 (retornada pelo /foto/upload-url). */
    public record SalvarFotoRequest(@NotBlank String url) {
    }

    /** Troca do PIN: senha atual (quando já existe) + nova senha (6 dígitos). */
    public record AlterarSenhaRequest(String senhaAtual, @NotBlank String senhaNova) {
    }
}
