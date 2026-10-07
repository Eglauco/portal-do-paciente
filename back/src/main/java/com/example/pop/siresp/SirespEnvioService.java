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
 * Reenvia o XML de um registro do SIRESP ao Sistema de Gestão da unidade, REPLICANDO o "Post XML" do CROSS: HTTP POST
 * {@code application/x-www-form-urlencoded} com um único parâmetro {@code msg} contendo o XML inteiro
 * (exatamente como o SIRESP faria — o receptor lê {@code $_POST[msg]}). A resposta do Sistema de Gestão é o XML
 * {@code <RETORNO><CODIGORETORNO>S|N</CODIGORETORNO><DESCRICAO/></RETORNO>}: {@code S} = processado,
 * qualquer outra coisa (ou HTTP ≠ 2xx / falha de conexão) = não processado. O resultado vai para o
 * {@code logEnvio} do registro + o {@code statusEnvio} (ENVIADO/FALHA) + o carimbo {@code enviadoEm}.
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

    /** Campos do XML de EXAME em ordem — usados para reconstruir o XML de exames sem arquivo no S3. */
    private static final CampoXml[] ORDEM_EXAME = {
            new CampoXml("TIPO_EXAME", Siresp::getTipoExame),
            new CampoXml("COD_UNIDADE_EXECUTANTE", Siresp::getCodUnidadeExecutante),
            new CampoXml("ID_AGE_EXAME_HOR", Siresp::getIdAgeExameHor),
            new CampoXml("ID_AGE_EXAME", Siresp::getIdAgeExame),
            new CampoXml("AGE_EXAME_NOME", Siresp::getAgeExameNome),
            new CampoXml("ID_ASSOCIACAO", Siresp::getIdAssociacao),
            new CampoXml("NOME_ASSOCIACAO", Siresp::getNomeAssociacao),
            new CampoXml("COD_DIA", Siresp::getCodDia),
            new CampoXml("DATA_AGENDA", Siresp::getDataAgenda),
            new CampoXml("HOR_INI", Siresp::getHorIni),
            new CampoXml("HOR_FIM", Siresp::getHorFim),
            new CampoXml("ID_MOTIVO", Siresp::getIdMotivo),
            new CampoXml("ID_PROFISSIONAL", Siresp::getIdProfissional),
            new CampoXml("DOC_PROFISSIONAL", Siresp::getDocProfissional),
            new CampoXml("ORIGEM", Siresp::getOrigem),
            new CampoXml("NOME_PROFISSIONAL", Siresp::getNomeProfissional),
            new CampoXml("ID_ESPECIALIDADE", Siresp::getIdEspecialidade),
            new CampoXml("NOME_ESPECIALIDADE", Siresp::getNomeEspecialidade),
            new CampoXml("ID_EXAME", Siresp::getIdExame),
            new CampoXml("COD_EXAME", Siresp::getCodExame),
            new CampoXml("NOME_EXAME", Siresp::getNomeExame),
            new CampoXml("TIPO_TABELA", Siresp::getTipoTabela),
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
     * Resposta do envio: {@code enviado} = houve tentativa de POST ao Sistema de Gestão (false quando não há URL);
     * {@code sucesso} = o Sistema de Gestão processou; {@code mensagem} = texto para o admin; {@code registro}
     * = o registro atualizado (com o {@code logEnvio}/{@code statusEnvio} do envio).
     */
    public record EnvioResponse(boolean enviado, boolean sucesso, String mensagem, Siresp registro) {
    }

    /**
     * Envia o XML do registro {@code id} ao Sistema de Gestão (HTTP POST, parâmetro {@code msg}) e grava o resultado
     * no {@code logEnvio} + {@code statusEnvio} (ENVIADO/FALHA) + {@code enviadoEm}. Nunca lança por falha de
     * rede/recusa — isso vira {@code sucesso=false} + FALHA.
     */
    public EnvioResponse enviar(Long id) {
        Siresp s = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado."));
        String url = configService.postUrl();
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Configure a URL do Sistema de Gestão (Post XML) nas Configurações do SIRESP antes de enviar.");
        }
        // Fonte do XML: os BYTES ORIGINAIS do S3 (sem nenhuma divergência) ou, para registros antigos sem arquivo,
        // o XML reconstruído das colunas (fallback).
        byte[] original = s.getArquivoUrl() == null ? null : storageService.baixarBytes(s.getArquivoUrl());
        boolean usouOriginal = original != null && original.length > 0;
        byte[] conteudo = usouOriginal ? original : reconstruir(s).getBytes(StandardCharsets.UTF_8);
        String fonte = usouOriginal ? "arquivo original" : "XML reconstruído";

        Envio e = postar(conteudo, url);
        registrarEnvio(s, linhaLog(fonte, e), e.sucesso());
        repository.save(s);
        return new EnvioResponse(true, e.sucesso(), e.mensagem(), s);
    }

    /**
     * Envia ao Sistema de Gestão o ARQUIVO ORIGINAL de um import recém-feito (os bytes exatos no S3) e grava o
     * resultado ({@code logEnvio}/{@code statusEnvio}/{@code enviadoEm}) em TODAS as linhas daquele upload
     * ({@code ids}). Usado pela rotina de importação quando o envio automático está ligado. Roda FORA da transação
     * do import (não segura conexão durante o HTTP) e nunca lança por falha de rede/recusa — vira {@code sucesso=false}
     * + FALHA. Devolve {@code enviado=false} (sem postar) se não houver URL ou o arquivo não estiver disponível (nesse
     * caso o status de envio fica como NAO_ENVIADO).
     */
    public EnvioResponse enviarDoImport(String arquivoUrl, List<Long> ids) {
        String url = configService.postUrl();
        if (url == null || url.isBlank()) {
            return new EnvioResponse(false, false, "URL do Sistema de Gestão não configurada — nada foi enviado.", null);
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
                    registrarEnvio(s, linha, e.sucesso());
                    repository.save(s);
                });
            }
        }
        return new EnvioResponse(true, e.sucesso(), e.mensagem(), null);
    }

    /** Grava o desfecho de um envio no registro: log de envio (último resultado) + status + carimbo. */
    private static void registrarEnvio(Siresp s, String linhaLog, boolean sucesso) {
        s.setLogEnvio(linhaLog);
        s.setEnviadoEm(LocalDateTime.now());
        s.setStatusEnvio(sucesso ? StatusEnvio.ENVIADO : StatusEnvio.FALHA);
    }

    /** Resultado interno de um POST ao Sistema de Gestão (sem tocar no banco). */
    private record Envio(boolean sucesso, String mensagem) {
    }

    /**
     * Faz o POST dos bytes ao Sistema de Gestão (parâmetro {@code msg}, byte a byte) e interpreta a resposta
     * ({@code <RETORNO>}/{@code CODIGORETORNO}). Não toca no banco e nunca lança — falhas viram {@code sucesso=false}.
     */
    private Envio postar(byte[] conteudo, String url) {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
            // Codifica os bytes preservando-os 1:1 (não recodifica charset) — o Sistema de Gestão recebe o original.
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
                return new Envio(false, "o Sistema de Gestão respondeu HTTP " + status + ".");
            }
            if (codigo == null) {
                // Sem <RETORNO> no corpo: considera entregue (HTTP 2xx).
                return new Envio(true, "entregue (HTTP " + status + ").");
            }
            if (codigo.equalsIgnoreCase("S")) {
                return new Envio(true, "processado pelo Sistema de Gestão (CODIGORETORNO=S).");
            }
            return new Envio(false, "o Sistema de Gestão não processou (CODIGORETORNO=" + codigo + ")"
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
        return "Envio ao Sistema de Gestão [" + fonte + "] (" + LocalDateTime.now().format(CARIMBO) + "): "
                + prefixo + " — " + e.mensagem();
    }

    /**
     * Codifica bytes como {@code application/x-www-form-urlencoded} preservando-os byte a byte (sem recodificar
     * charset). Garante que o Sistema de Gestão receba EXATAMENTE o arquivo original, independente do encoding do XML.
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

    private static String motivo(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isBlank() ? e.getClass().getSimpleName() : m) + ".";
    }

    /** Fallback para registros sem arquivo no S3: remonta um XML auto-contido a partir das colunas (por tipo). */
    private static String reconstruir(Siresp s) {
        CampoXml[] ordem = s.getTipoRegistro() == TipoRegistroSiresp.EXAME ? ORDEM_EXAME : ORDEM;
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" standalone=\"yes\"?>\n<NewDataSet>\n<Mensagem>");
        for (CampoXml c : ordem) {
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
