package com.afrismart.ai.engine;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.afrismart.ai.AiProperties;
import com.afrismart.ai.tool.ToolDefinition;
import com.afrismart.shared.api.ApiException;
import com.afrismart.shared.api.ErrorCodes;

/** Works with any OpenAI-compatible Chat Completions API (OpenAI, Groq, OpenRouter, Ollama, Gemini's OpenAI endpoint). */
@Component
public class OpenAiEngine implements ModelEngine {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEngine.class);

    private final AiProperties properties;
    private final RestClient restClient;

    public OpenAiEngine(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(properties.getOpenai().getTimeout());
        this.restClient = builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(stripTrailingSlash(properties.getOpenai().getBaseUrl()))
                .build();
    }

    @Override
    public boolean supports(AiEngineType type) {
        return type == AiEngineType.OPENAI;
    }

    @Override
    public boolean isConfigured() {
        String key = properties.getOpenai().getApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String model() {
        return properties.getOpenai().getModel();
    }

    @Override
    public ChatMessage complete(List<ChatMessage> messages, List<ToolDefinition> tools) {
        AiProperties.OpenAi config = properties.getOpenai();
        ChatCompletionRequest request = new ChatCompletionRequest(
                config.getModel(),
                messages,
                tools.isEmpty() ? null : tools.stream().map(ChatCompletionRequest.ToolSpec::of).toList(),
                tools.isEmpty() ? null : "auto",
                config.getTemperature()
        );
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().getFirst().message() == null) {
                throw new ApiException(ErrorCodes.AI_ENGINE_ERROR, "AI engine returned no answer", HttpStatus.BAD_GATEWAY);
            }
            return response.choices().getFirst().message();
        } catch (RestClientResponseException ex) {
            log.warn("OpenAI-compatible call failed: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ApiException(
                    ErrorCodes.AI_ENGINE_ERROR,
                    "AI engine request failed (" + ex.getStatusCode().value() + ")",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (RestClientException ex) {
            log.warn("OpenAI-compatible call failed", ex);
            throw new ApiException(ErrorCodes.AI_ENGINE_ERROR, "AI engine is unreachable", HttpStatus.BAD_GATEWAY);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
