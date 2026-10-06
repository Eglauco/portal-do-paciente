package com.example.pop.siresp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.pop.storage.StorageService;

/**
 * Reenvia o XML de um registro do SIRESP ao cliente, REPLICANDO o "Post XML" do CROSS: HTTP POST
 * {@code application/x-www-form-urlencoded} com um único parâmetro {@code msg} contendo o XML inteiro
 * (exatamente como o SIRESP faria — o receptor lê {@code $_POST[msg]}). A resposta do cliente é o XML
 * {@code <RETORNO><CODIGORETORNO>S|N</CODIGORETORNO><DESCRICAO/></RETORNO>}: {@code S} = processado,
 * qualquer outra coisa (ou HTTP ≠ 2xx / falha de conexão) = não processado. O resultado é anexado ao
 * Log de integração do registro.
 */
@Service
public class SirespEnvioService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Pattern CODIGO = Pattern.compile("(?is)<CODIGORETORNO>\\s*(.*?)\\s*</CODIGORETORNO>");
    private static final Pattern DESCRICAO = Pattern.compile("(?is)<DESCRICAO>\\s*(.*?)\\s*</DESCRICAO>");
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** Campos do XML em ordem (tag → valor), usados para reconstruir o XML de registros antigos (sem arquivo no S3). */
    private static final CampoXml[] ORDEM = {
            new CampoXml("TIPO_CONSULTA", Siresp::getTipoConsulta),
            new CampoXml("COD_UNIDADE_EXECUTANTE", Siresp::getCodUnidadeExecutante),
            new CampoXml("ID_AGE_CONSULTA_HOR", Siresp::getIdAgeConsultaHor),
            new CampoXml("ID_AGE_CONSULTA", Siresp::getIdAgeConsulta),
            new CampoXml("AGE_CONSULTA_NOME", Siresp::getAgeConsultaNome),
            new CampoXml("ID_ESPECIALIDADE", Siresp::getIdEspecialidade),
            new CampoXml("NOME_ESPECIALIDADE", Siresp::getNomeEspecialidade),
            new CampoXml("COD_DIA", Siresp::getCodDia),
            new CampoXml("DATA_AGENDA", Siresp::getDataAgenda),
            new CampoXml("HOR_INI", Siresp::getHorIni),
            new CampoXml("HOR_FIM", Siresp::getHorFim),
            new CampoXml("TIPO", Siresp::getTipo),
            new CampoXml("ID_MOTIVO", Siresp::getIdMotivo),
            new CampoXml("ID_PROFISSIONAL", Siresp::getIdProfissional),
            new CampoXml("DOC_PROFISSIONAL", Siresp::getDocProfissional),
            new CampoXml("ORIGEM", Siresp::getOrigem),
            new CampoXml("NOME_PROFISSIONAL", Siresp::getNomeProfissional),
            new CampoXml("ID_PROTOCOLO", Siresp::getIdProtocolo),
            new CampoXml("SUBCATEG", Siresp::getSubcateg),
            new CampoXml("NOME_PROTOCOLO", Siresp::getNomeProtocolo),
            new CampoXml("COD_UNIDADE_SOLICITANTE", Siresp::getCodUnidadeSolicitante),
            new CampoXml("NOME_UNIDADE_SOLICITANTE", Siresp::getNomeUnidadeSolicitante),
            new CampoXml("CNES_UNIDADE_SOLICITANTE", Siresp::getCnesUnidadeSolicitante),
            new CampoXml("NOME_USUARIO_SOLICITANTE", Siresp::getNomeUsuarioSolicitante),
            new CampoXml("DT_ULTIMA_ATUALIZ", Siresp::getDtUltimaAtualiz),
            new CampoXml("COD_PACIENTE", Siresp::getCodPaciente),
            new CampoXml("NOME_PACIENTE", Siresp::getNomePaciente),
            new CampoXml("SEXO", Siresp::getSexo),
            new CampoXml("DT_NASCIMENTO", Siresp::getDtNascimento),
            new CampoXml("RG", Siresp::getRg),
            new CampoXml("CPF", Siresp::getCpf),
            new CampoXml("NOME_MAE", Siresp::getNomeMae),
            new CampoXml("NOME_PAI", Siresp::getNomePai),
            new CampoXml("ENDERECO", Siresp::getEndereco),
            new CampoXml("ENDERECO_NUMERO", Siresp::getEnderecoNumero),
            new CampoXml("BAIRRO", Siresp::getBairro),
            new CampoXml("MUNICIPIO", Siresp::getMunicipio),
            new CampoXml("UF", Siresp::getUf),
            new CampoXml("CEP", Siresp::getCep),
            new CampoXml("TEL_RES_DDD", Siresp::getTelResDdd),
            new CampoXml("TEL_RES", Siresp::getTelRes),
            new CampoXml("TEL_CELULAR_DDD", Siresp::getTelCelularDdd),
            new CampoXml("TEL_CELULAR", Siresp::getTelCelular),
            new CampoXml("TEL_COM_DDD", Siresp::getTelComDdd),
            new CampoXml("TEL_COM", Siresp::getTelCom),
            new CampoXml("TEL_COM_RAMAL", Siresp::getTelComRamal),
            new CampoXml("EMAIL", Siresp::getEmail),
            new CampoXml("CONTATO_NOME", Siresp::getContatoNome),
            new CampoXml("CONTATO_TEL_DDD", Siresp::getContatoTelDdd),
            new CampoXml("CONTATO_TEL", Siresp::getContatoTel),
            new CampoXml("NUM_CNS", Siresp::getNumCns),
            new CampoXml("NUM_PRONTUARIO", Siresp::getNumProntuario),
    };

    private final SirespRepository repository;
    private final SirespConfigService configService;
    private final StorageService storageService;

    public SirespEnvioService(SirespRepository repository, SirespConfigService configService,
            StorageService storageService) {
        this.repository = repository;
        this.configService = configService;
        this.storageService = storageService;
    }

    private interface Getter {
        String get(Siresp s);
    }

    private record CampoXml(String tag, Getter getter) {
    }

    /**
     * Resposta do envio/combinado: {@code enviado} = houve tentativa de POST ao cliente (false quando só reprocessou
     * por falta de URL); {@code sucesso} = o cliente processou; {@code mensagem} = texto para o admin; {@code registro}
     * = o registro atualizado (com o log recalculado e/ou o resultado do envio anexado).
     */
    public record EnvioResponse(boolean enviado, boolean sucesso, String mensagem, Siresp registro) {
    }

    /**
     * Envia o XML do registro {@code id} ao cliente (HTTP POST, parâmetro {@code msg}) e anexa o resultado ao Log
     * de integração. Nunca lança por falha de rede/recusa do cliente — isso vira {@code sucesso=false} + log.
     */
    public EnvioResponse enviar(Long id) {
        Siresp s = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado."));
        String url = configService.postUrl();
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Configure a URL de envio (Post XML) nas Configurações do SIRESP antes de enviar.");
        }
        // Fonte do XML: os BYTES ORIGINAIS do S3 (sem nenhuma divergência) ou, para registros antigos sem arquivo,
        // o XML reconstruído das colunas (fallback).
        byte[] original = s.getArquivoUrl() == null ? null : storageService.baixarBytes(s.getArquivoUrl());
        boolean usouOriginal = original != null && original.length > 0;
        byte[] conteudo = usouOriginal ? original : reconstruir(s).getBytes(StandardCharsets.UTF_8);
        String fonte = usouOriginal ? "arquivo original" : "XML reconstruído";

        Envio e = postar(conteudo, url);
        s.setLogIntegracao(anexar(s.getLogIntegracao(), linhaLog(fonte, e)));
        repository.save(s);
        return new EnvioResponse(true, e.sucesso(), e.mensagem(), s);
    }

    /**
     * Envia ao cliente o ARQUIVO ORIGINAL de um import recém-feito (os bytes exatos no S3) e anexa o resultado ao
     * Log de integração de TODAS as linhas daquele upload ({@code ids}). Usado pela rotina de importação quando o
     * envio automático está ligado. Roda FORA da transação do import (não segura conexão durante o HTTP) e nunca
     * lança por falha de rede/recusa — vira {@code sucesso=false} + log. Devolve {@code enviado=false} (sem postar)
     * se não houver URL ou o arquivo não estiver disponível.
     */
    public EnvioResponse enviarDoImport(String arquivoUrl, List<Long> ids) {
        String url = configService.postUrl();
        if (url == null || url.isBlank()) {
            return new EnvioResponse(false, false, "URL de envio não configurada — nada foi enviado.", null);
        }
        byte[] conteudo = arquivoUrl == null ? null : storageService.baixarBytes(arquivoUrl);
        if (conteudo == null || conteudo.length == 0) {
            return new EnvioResponse(false, false,
                    "arquivo original indisponível no armazenamento — nada foi enviado.", null);
        }
        Envio e = postar(conteudo, url);
        String linha = linhaLog("arquivo original", e);
        if (ids != null) {
            for (Long id : ids) {
                repository.findById(id).ifPresent(s -> {
                    s.setLogIntegracao(anexar(s.getLogIntegracao(), linha));
                    repository.save(s);
                });
            }
        }
        return new EnvioResponse(true, e.sucesso(), e.mensagem(), null);
    }

    /** Resultado interno de um POST ao cliente (sem tocar no banco). */
    private record Envio(boolean sucesso, String mensagem) {
    }

    /**
     * Faz o POST dos bytes ao cliente (parâmetro {@code msg}, byte a byte) e interpreta a resposta
     * ({@code <RETORNO>}/{@code CODIGORETORNO}). Não toca no banco e nunca lança — falhas viram {@code sucesso=false}.
     */
    private Envio postar(byte[] conteudo, String url) {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
            // Codifica os bytes preservando-os 1:1 (não recodifica charset) — o cliente recebe exatamente o original.
            String corpo = "msg=" + formEncode(conteudo);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.US_ASCII))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = resp.statusCode();
            String codigo = extrair(CODIGO, resp.body());
            String descricao = extrair(DESCRICAO, resp.body());
            if (status < 200 || status >= 300) {
                return new Envio(false, "o cliente respondeu HTTP " + status + ".");
            }
            if (codigo == null) {
                // Sem <RETORNO> no corpo: considera entregue (HTTP 2xx).
                return new Envio(true, "entregue (HTTP " + status + ").");
            }
            if (codigo.equalsIgnoreCase("S")) {
                return new Envio(true, "processado pelo cliente (CODIGORETORNO=S).");
            }
            return new Envio(false, "o cliente não processou (CODIGORETORNO=" + codigo + ")"
                    + (descricao == null || descricao.isBlank() ? "." : " — " + descricao + "."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Envio(false, "o envio foi interrompido.");
        } catch (Exception e) {
            return new Envio(false, "falha de conexão: " + motivo(e));
        }
    }

    /** Monta a linha de log do envio (fonte + carimbo + SUCESSO/FALHA + mensagem). */
    private static String linhaLog(String fonte, Envio e) {
        String prefixo = e.sucesso() ? "SUCESSO" : "FALHA";
        return "Envio ao cliente [" + fonte + "] (" + LocalDateTime.now().format(CARIMBO) + "): "
                + prefixo + " — " + e.mensagem();
    }

    /**
     * Codifica bytes como {@code application/x-www-form-urlencoded} preservando-os byte a byte (sem recodificar
     * charset). Garante que o cliente receba EXATAMENTE o arquivo original, independentemente do encoding do XML.
     */
    private static String formEncode(byte[] raw) {
        StringBuilder sb = new StringBuilder(raw.length * 3);
        for (byte value : raw) {
            int c = value & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '*') {
                sb.append((char) c);
            } else if (c == ' ') {
                sb.append('+');
            } else {
                sb.append('%').append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
            }
        }
        return sb.toString();
    }

    /** Extrai o conteúdo do primeiro grupo do padrão no corpo (limitado a 500 chars), ou null se não casar. */
    private static String extrair(Pattern p, String corpo) {
        if (corpo == null) {
            return null;
        }
        Matcher m = p.matcher(corpo);
        if (!m.find()) {
            return null;
        }
        String v = m.group(1);
        if (v == null) {
            return null;
        }
        v = v.trim();
        return v.length() > 500 ? v.substring(0, 500) + "…" : v;
    }

    /** Anexa uma linha ao log existente (separada por linha em branco), preservando o diagnóstico anterior. */
    private static String anexar(String atual, String linha) {
        if (atual == null || atual.isBlank()) {
            return linha;
        }
        return atual + "\n\n" + linha;
    }

    private static String motivo(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isBlank() ? e.getClass().getSimpleName() : m) + ".";
    }

    /** Fallback para registros antigos (sem arquivo no S3): remonta um XML auto-contido a partir das colunas. */
    private static String reconstruir(Siresp s) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" standalone=\"yes\"?>\n<NewDataSet>\n<Mensagem>");
        for (CampoXml c : ORDEM) {
            String v = c.getter().get(s);
            if (v != null) {
                sb.append('<').append(c.tag()).append('>').append(escapar(v)).append("</").append(c.tag()).append('>');
            }
        }
        return sb.append("</Mensagem>\n</NewDataSet>").toString();
    }

    private static String escapar(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
