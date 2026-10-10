package com.example.pop.plataforma;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.inquilino.SuperAdminAutenticacao;
import com.example.pop.storage.StorageService;
import com.example.pop.storage.UploadUrlRequest;
import com.example.pop.storage.UploadUrlResponse;
import com.example.pop.tema.PaletaTema;
import com.example.pop.tema.TemaService;

import jakarta.validation.Valid;

/**
 * Identidade de PLATAFORMA gerenciada pelo super-admin: cor, logomarca, imagem de fundo e frases do
 * login (os defaults do pré-login e o fallback dos inquilinos). Fica sob {@code /superadmin/**}
 * ({@code permitAll} no {@code SecurityConfig}) e é protegido pelo segredo {@code X-SuperAdmin-Secret}
 * conferido aqui — NUNCA por {@code ROLE_ADMIN} (que é por-inquilino). Por isso o upload de imagens
 * tem rota PRÓPRIA aqui (o {@code /storage/**} exige ADMIN, que o super-admin não tem).
 */
@RestController
@RequestMapping("/superadmin/plataforma")
public class ConfigPlataformaController {

    /** Validade da URL assinada do preview — longa p/ o cache do front não servir link morto. */
    private static final Duration VALIDADE_IMAGEM = Duration.ofHours(24);
    /** Pasta dedicada no bucket p/ as imagens de plataforma (separa das do inquilino). */
    private static final String PASTA = "plataforma";

    private final SuperAdminAutenticacao autenticacao;
    private final ConfiguracaoPlataformaService service;
    private final StorageService storageService;
    private final TemaService temaService;

    public ConfigPlataformaController(SuperAdminAutenticacao autenticacao, ConfiguracaoPlataformaService service,
            StorageService storageService, TemaService temaService) {
        this.autenticacao = autenticacao;
        this.service = service;
        this.storageService = storageService;
        this.temaService = temaService;
    }

    /** Identidade atual da plataforma (p/ a tela do super-admin). Imagens com URL assinada p/ preview. */
    @GetMapping("/config")
    public ConfigPlataformaResponse config(
            @RequestHeader(value = "X-SuperAdmin-Secret", required = false) String secret) {
        autenticacao.conferir(secret);
        return montar();
    }

    /** Salva a identidade da plataforma; remove do S3 as imagens que ficaram órfãs (evita lixo + vazamento). */
    @PutMapping("/config")
    public ConfigPlataformaResponse salvar(
            @RequestHeader(value = "X-SuperAdmin-Secret", required = false) String secret,
            @RequestBody ConfigPlataformaRequest request) {
        autenticacao.conferir(secret);
        // As imagens só podem apontar p/ objetos da PRÓPRIA pasta de plataforma: impede que uma URL de
        // outra pasta do bucket (ex.: prontuarios/<laudo>) vire link assinado público servido no pré-login.
        exigirNaPastaDePlataforma(request.logoUrl());
        exigirNaPastaDePlataforma(request.loginFundoUrl());

        String logoAntiga = service.valorImagem(ChaveConfiguracao.LOGO_PLATAFORMA);
        String fundoAntigo = service.valorImagem(ChaveConfiguracao.LOGIN_FUNDO);

        service.salvar(request, null);

        // URLs que CONTINUAM referenciadas após salvar — nunca apagar uma delas (ex.: logo e fundo iguais).
        Set<String> aindaEmUso = new HashSet<>();
        adicionarSeUtil(aindaEmUso, request.logoUrl());
        adicionarSeUtil(aindaEmUso, request.loginFundoUrl());
        excluirSeOrfa(logoAntiga, aindaEmUso);
        excluirSeOrfa(fundoAntigo, aindaEmUso);
        return montar();
    }

    /** 422 se a URL de imagem (quando informada) não pertence à pasta de plataforma no bucket. */
    private void exigirNaPastaDePlataforma(String url) {
        if (url != null && !url.isBlank() && !storageService.urlNaPasta(url, PASTA)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "URL de imagem inválida: envie a imagem pelo próprio formulário.");
        }
    }

    private static void adicionarSeUtil(Set<String> conjunto, String url) {
        if (url != null && !url.isBlank()) {
            conjunto.add(url);
        }
    }

    /** Gera a URL pré-assinada (PUT) p/ o super-admin subir a imagem direto ao S3 (pasta "plataforma"). */
    @PostMapping("/upload-url")
    public UploadUrlResponse uploadUrl(
            @RequestHeader(value = "X-SuperAdmin-Secret", required = false) String secret,
            @Valid @RequestBody UploadUrlRequest request) {
        autenticacao.conferir(secret);
        try {
            return storageService.gerarUploadUrl(request.nomeArquivo(), request.contentType(), PASTA);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível preparar o upload. Verifique a configuração do armazenamento.");
        }
    }

    /** Prévia da paleta derivada de uma cor candidata (equivalente ao /tema/preview, mas sob o segredo). */
    @GetMapping("/tema-preview")
    public PaletaTema temaPreview(
            @RequestHeader(value = "X-SuperAdmin-Secret", required = false) String secret,
            @RequestParam String cor) {
        autenticacao.conferir(secret);
        return temaService.derivar(cor);
    }

    /** Monta a resposta com os valores atuais + URLs assinadas das imagens. */
    private ConfigPlataformaResponse montar() {
        String logo = service.valorImagem(ChaveConfiguracao.LOGO_PLATAFORMA);
        String fundo = service.valorImagem(ChaveConfiguracao.LOGIN_FUNDO);
        return new ConfigPlataformaResponse(
                service.valorCor(ChaveConfiguracao.COR_PRIMARIA_PLATAFORMA),
                service.valorTexto(ChaveConfiguracao.NOME_PLATAFORMA),
                service.valorTexto(ChaveConfiguracao.LOGIN_TITULO),
                service.valorTexto(ChaveConfiguracao.LOGIN_SUBTITULO),
                logo,
                logo == null ? null : storageService.urlVisualizacao(logo, VALIDADE_IMAGEM),
                fundo,
                fundo == null ? null : storageService.urlVisualizacao(fundo, VALIDADE_IMAGEM));
    }

    /**
     * Remove do S3 a imagem antiga QUANDO ela não é mais referenciada por nenhum campo após o salvar
     * (best-effort — não quebra o salvar). Guarda o caso de logo e fundo apontarem para a mesma URL.
     */
    private void excluirSeOrfa(String antiga, Set<String> aindaEmUso) {
        if (antiga != null && !antiga.isBlank() && !aindaEmUso.contains(antiga)) {
            try {
                storageService.excluirPorUrl(antiga);
            } catch (RuntimeException ignore) {
                // best-effort: objeto órfão não impede o salvamento
            }
        }
    }
}
