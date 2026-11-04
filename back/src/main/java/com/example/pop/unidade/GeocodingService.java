package com.example.pop.unidade;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Geocodificação de endereço → coordenadas via Nominatim (OpenStreetMap), gratuito. Usado ao salvar a
 * unidade para guardar lat/lng e o app desenhar o mapa nativo (sem geocodificar no aparelho, evitando
 * pedir permissão de localização). Fail-soft: qualquer falha devolve vazio (unidade salva sem mapa).
 *
 * <p>O Nominatim costuma NÃO casar o endereço com número de casa (ex.: "Av. General Glicério, 100 - …").
 * Por isso há um <b>fallback</b> do mais específico ao mais amplo: endereço completo → rua + cidade/UF →
 * cidade/UF. Precisão de rua/bairro já basta para a miniatura do mapa. A resposta ({@code limit=1}) é lida
 * por regex (o Nominatim devolve {@code "lat"}/{@code "lon"} como strings), isolando do Jackson do projeto.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    /** Nominatim exige um User-Agent identificável. */
    private static final String USER_AGENT = "IntegraSaude/1.0 (app clinico; contato trilharsaude.app.br)";

    private static final Pattern LAT = Pattern.compile("\"lat\"\\s*:\\s*\"(-?\\d+(?:\\.\\d+)?)\"");
    private static final Pattern LON = Pattern.compile("\"lon\"\\s*:\\s*\"(-?\\d+(?:\\.\\d+)?)\"");
    /** Tipos de logradouro no início do 1º trecho, removidos para o Nominatim casar a rua. */
    private static final Pattern LOGRADOURO =
            Pattern.compile("(?i)^\\s*(av\\.?|avenida|r\\.?|rua|rod\\.?|rodovia|al\\.?|alameda|pra[cç]a|travessa|tv\\.?)\\s+");

    // Criado PREGUIÇOSAMENTE (na 1ª geocodificação), não no construtor: montar o HttpClient abre um
    // socketpair de loopback (wakeup do selector NIO) já na construção do bean, o que quebra o START
    // da aplicação em ambientes restritos (CI/sandbox sem socket). Lazy = o contexto sobe sem tocar na
    // rede; o comportamento do geocode é idêntico (o client é criado na 1ª chamada e reaproveitado).
    private volatile HttpClient http;

    /** HttpClient compartilhado, criado sob demanda (double-checked locking). */
    private HttpClient http() {
        HttpClient local = http;
        if (local == null) {
            synchronized (this) {
                local = http;
                if (local == null) {
                    local = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                    http = local;
                }
            }
        }
        return local;
    }

    /** Coordenadas [latitude, longitude] do endereço (tenta do mais específico ao mais amplo). */
    public Optional<double[]> geocodificar(String endereco) {
        if (endereco == null || endereco.isBlank()) {
            return Optional.empty();
        }
        for (String consulta : candidatos(endereco)) {
            Optional<double[]> coords = consultar(consulta);
            if (coords.isPresent()) {
                return coords;
            }
        }
        return Optional.empty();
    }

    /** Monta as tentativas: endereço completo, depois "rua, cidade/UF", depois só "cidade/UF". */
    private List<String> candidatos(String endereco) {
        LinkedHashSet<String> qs = new LinkedHashSet<>();
        String completo = endereco.trim();
        qs.add(completo);
        String[] partes = completo.split(",");
        if (partes.length >= 2) {
            String cidadeUf = partes[partes.length - 1].trim().replace(" - ", ", ");
            String rua = limparRua(partes[0]);
            if (!rua.isBlank() && !cidadeUf.isBlank()) {
                qs.add(rua + ", " + cidadeUf);
            }
            if (!cidadeUf.isBlank()) {
                qs.add(cidadeUf);
            }
        }
        return new ArrayList<>(qs);
    }

    /** Remove número da casa e o tipo de logradouro ("Av. General Glicério, 100" → "General Glicério"). */
    private static String limparRua(String trecho) {
        String r = trecho.replaceAll("\\d+", " ");
        r = LOGRADOURO.matcher(r).replaceFirst("");
        return r.replaceAll("[\\s\\-,]+$", "").trim();
    }

    private Optional<double[]> consultar(String query) {
        try {
            String q = URLEncoder.encode(query + ", Brasil", StandardCharsets.UTF_8);
            URI uri = URI.create("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + q);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Accept-Language", "pt-BR")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Geocodificação retornou status {}", resp.statusCode());
                return Optional.empty();
            }
            Matcher mLat = LAT.matcher(resp.body());
            Matcher mLon = LON.matcher(resp.body());
            if (mLat.find() && mLon.find()) {
                return Optional.of(new double[] {
                        Double.parseDouble(mLat.group(1)),
                        Double.parseDouble(mLon.group(1)),
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Geocodificação interrompida");
        } catch (Exception e) {
            log.warn("Falha ao geocodificar '{}': {}", query, e.getMessage());
        }
        return Optional.empty();
    }
}
