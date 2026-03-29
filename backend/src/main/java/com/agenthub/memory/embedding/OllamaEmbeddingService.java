package com.agenthub.memory.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Generates embeddings using Ollama's /api/embeddings endpoint.
 */
@Component
@ConditionalOnProperty(name = "agenthub.memory.vector.enabled", havingValue = "true")
public class OllamaEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String embeddingModel;

    public OllamaEmbeddingService(
            @Value("${agenthub.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${agenthub.memory.embedding.model:nomic-embed-text}") String embeddingModel,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder().baseUrl(ollamaBaseUrl).build();
        this.objectMapper = objectMapper;
        this.embeddingModel = embeddingModel;
        log.info("Initialized OllamaEmbeddingService with model: {}", embeddingModel);
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new EmbeddingException("Cannot embed null or blank text");
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", embeddingModel,
                    "prompt", text
            );

            String response = webClient.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.path("embedding");

            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new EmbeddingException("Ollama returned empty embedding for model: " + embeddingModel);
            }

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            log.debug("Generated embedding of dimension {} for text of length {}", embedding.length, text.length());
            return embedding;

        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
}
