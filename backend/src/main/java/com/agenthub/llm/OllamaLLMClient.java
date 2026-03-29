package com.agenthub.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class OllamaLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLLMClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String defaultModel;

    public OllamaLLMClient(
            @Value("${agenthub.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${agenthub.ollama.default-model:mistral}") String defaultModel,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.defaultModel = defaultModel;
        log.info("Initialized OllamaLLMClient with base URL: {} and model: {}", baseUrl, defaultModel);
    }

    @Override
    @Retry(name = "llmClient")
    public String generate(String prompt) {
        return generate(prompt, LLMOptions.defaults());
    }

    @Override
    @Retry(name = "llmClient")
    public String generate(String prompt, LLMOptions options) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String model = options.model() != null ? options.model() : defaultModel;

        try {
            log.debug("Sending prompt to Ollama model '{}', length={}", model, prompt.length());

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", options.temperature(),
                            "num_predict", options.maxTokens(),
                            "top_p", options.topP()
                    )
            );

            String responseJson = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(options.timeoutSeconds()))
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            String result = root.path("response").asText();

            sample.stop(Timer.builder("llm.generate.duration")
                    .tag("model", model)
                    .tag("status", "success")
                    .register(meterRegistry));

            log.debug("Received response from Ollama, length={}", result.length());
            return result;

        } catch (Exception e) {
            sample.stop(Timer.builder("llm.generate.duration")
                    .tag("model", model)
                    .tag("status", "error")
                    .register(meterRegistry));

            meterRegistry.counter("llm.generate.errors", "model", model).increment();
            log.error("Error calling Ollama model '{}': {}", model, e.getMessage(), e);
            throw new LLMException("Failed to generate response from Ollama: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<String> generateAsync(String prompt) {
        return generateAsync(prompt, LLMOptions.defaults());
    }

    @Override
    public CompletableFuture<String> generateAsync(String prompt, LLMOptions options) {
        String model = options.model() != null ? options.model() : defaultModel;

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "options", Map.of(
                        "temperature", options.temperature(),
                        "num_predict", options.maxTokens(),
                        "top_p", options.topP()
                )
        );

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(options.timeoutSeconds()))
                .map(responseJson -> {
                    try {
                        JsonNode root = objectMapper.readTree(responseJson);
                        return root.path("response").asText();
                    } catch (Exception e) {
                        throw new LLMException("Failed to parse Ollama response", e);
                    }
                })
                .toFuture();
    }

    @Override
    public Flux<String> stream(String prompt) {
        return stream(prompt, LLMOptions.defaults());
    }

    @Override
    public Flux<String> stream(String prompt, LLMOptions options) {
        String model = options.model() != null ? options.model() : defaultModel;

        log.debug("Starting streaming from Ollama model '{}', prompt length={}", model, prompt.length());

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", true,
                "options", Map.of(
                        "temperature", options.temperature(),
                        "num_predict", options.maxTokens(),
                        "top_p", options.topP()
                )
        );

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(options.timeoutSeconds()))
                .mapNotNull(line -> {
                    try {
                        JsonNode root = objectMapper.readTree(line);
                        if (root.path("done").asBoolean(false)) {
                            return null; // final message, skip
                        }
                        return root.path("response").asText("");
                    } catch (Exception e) {
                        log.warn("Failed to parse streaming chunk: {}", e.getMessage());
                        return null;
                    }
                })
                .doOnError(e -> {
                    meterRegistry.counter("llm.stream.errors", "model", model).increment();
                    log.error("Streaming error from Ollama model '{}': {}", model, e.getMessage());
                })
                .doOnComplete(() -> log.debug("Streaming completed from Ollama model '{}'", model));
    }
}
