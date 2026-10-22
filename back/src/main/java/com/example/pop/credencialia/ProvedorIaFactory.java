package com.example.pop.credencialia;

import java.time.Duration;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Monta o {@link ChatModel} (LangChain4j) do provedor de IA escolhido num escopo, a partir da credencial
 * resolvida (provedor + chave + base URL + modelo + limites). É o ÚNICO ponto que conhece cada provedor —
 * PLUGAR um provedor novo = adicionar o módulo {@code langchain4j-<provedor>} ao pom e um {@code case}
 * aqui (+ semear os modelos dele no cadastro "Modelos de IA"). Nada mais no projeto muda.
 */
@Component
public class ProvedorIaFactory {

    /** Provedores que já têm implementação aqui (o front só oferece provedores suportados COM modelos ativos). */
    public boolean suportado(TipoProvedorIa tipo) {
        return tipo == TipoProvedorIa.ANTHROPIC
                || tipo == TipoProvedorIa.OPENAI
                || tipo == TipoProvedorIa.GOOGLE_GEMINI;
    }

    /**
     * Constrói um ChatModel pronto para uso. {@code maxTokens}/{@code timeout} entram na construção do
     * modelo (por isso o cache de modelos é por escopo+modelo+limites). {@code baseUrl} em branco = padrão
     * do provedor.
     */
    public ChatModel montar(TipoProvedorIa provedor, String apiKey, String baseUrl, String modelo,
            long maxTokens, Duration timeout) {
        return switch (provedor) {
            case ANTHROPIC -> {
                AnthropicChatModel.AnthropicChatModelBuilder b = AnthropicChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(modelo)
                        .maxTokens((int) maxTokens)
                        .timeout(timeout)
                        // Nunca reexecutar a chamada de IA: a moderação é fail-closed e SÍNCRONA no request
                        // do paciente — com o default (2) um 429/529 seguraria o thread ~3x o timeout. O timeout
                        // é POR tentativa, então mantemos 1 tentativa só (igual era com o SDK anthropic direto).
                        .maxRetries(0);
                if (StringUtils.hasText(baseUrl)) {
                    b.baseUrl(baseUrl.trim());
                }
                yield b.build();
            }
            case OPENAI -> {
                OpenAiChatModel.OpenAiChatModelBuilder b = OpenAiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(modelo)
                        .maxTokens((int) maxTokens)
                        .timeout(timeout)
                        .maxRetries(0); // mesma decisão de não reexecutar chamada de IA (ver case ANTHROPIC)
                if (StringUtils.hasText(baseUrl)) {
                    b.baseUrl(baseUrl.trim()); // permite Azure OpenAI / gateway compatível
                }
                yield b.build();
            }
            case GOOGLE_GEMINI -> {
                // Google AI (API key). Gemini usa maxOutputTokens (não maxTokens) e tem visão nativa.
                // Decisão POR MODELO: os Pro EXIGEM "thinking" (rejeitam budget 0); os Flash funcionam
                // melhor com thinking OFF (barato e cabe em orçamento pequeno — teste=24, moderação=120).
                boolean exigeThinking = modelo.toLowerCase(Locale.ROOT).contains("pro");
                GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder b = GoogleAiGeminiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(modelo)
                        .timeout(timeout)
                        .maxRetries(0);
                if (exigeThinking) {
                    // Pro: deixa pensar e GARANTE orçamento de saída suficiente — o raciocínio conta no
                    // maxOutputTokens, então orçamento pequeno (24/120) truncaria e voltaria vazio.
                    b.maxOutputTokens(Math.max((int) maxTokens, 2048));
                } else {
                    b.maxOutputTokens((int) maxTokens).thinkingConfig(new GeminiThinkingConfig(false, 0, null));
                }
                if (StringUtils.hasText(baseUrl)) {
                    b.baseUrl(baseUrl.trim());
                }
                yield b.build();
            }
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Provedor de IA '" + provedor + "' ainda não está disponível. Use Anthropic ou plugue o "
                            + "provedor (módulo langchain4j-" + provedor.name().toLowerCase() + " + um case no ProvedorIaFactory).");
        };
    }
}
