package com.agenthub.memory;

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
import java.util.*;

/**
 * ChromaDB-backed long-term memory implementation.
 * Uses Ollama for embedding generation and ChromaDB for vector storage/retrieval.
 */
@Component
@ConditionalOnProperty(name = "agenthub.memory.chroma.enabled", havingValue = "true", matchIfMissing = true)
public class ChromaLongTermMemory implements LongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(ChromaLongTermMemory.class);

    private final WebClient chromaClient;
    private final WebClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final String collectionName;
    private final String embeddingModel;

    private String collectionId;

    public ChromaLongTermMemory(
            @Value("${agenthub.memory.chroma.base-url:http://localhost:8000}") String chromaBaseUrl,
            @Value("${agenthub.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${agenthub.memory.chroma.collection:agenthub_knowledge}") String collectionName,
            @Value("${agenthub.memory.chroma.embedding-model:nomic-embed-text}") String embeddingModel,
            ObjectMapper objectMapper
    ) {
        this.chromaClient = WebClient.builder().baseUrl(chromaBaseUrl).build();
        this.ollamaClient = WebClient.builder().baseUrl(ollamaBaseUrl).build();
        this.objectMapper = objectMapper;
        this.collectionName = collectionName;
        this.embeddingModel = embeddingModel;
        initCollection();
    }

    private void initCollection() {
        try {
            // Try to get or create the collection
            Map<String, Object> body = Map.of(
                    "name", collectionName,
                    "metadata", Map.of("description", "AgentHub knowledge base")
            );

            String response = chromaClient.post()
                    .uri("/api/v1/collections")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            this.collectionId = root.path("id").asText();
            log.info("ChromaDB collection '{}' ready with ID: {}", collectionName, collectionId);
        } catch (Exception e) {
            log.warn("Could not initialize ChromaDB collection '{}': {}. " +
                     "Long-term memory will be unavailable.", collectionName, e.getMessage());
        }
    }

    @Override
    public void store(String content, String documentId) {
        if (collectionId == null) {
            log.warn("ChromaDB not available, skipping store");
            return;
        }

        try {
            List<String> chunks = chunkText(content, 500, 50);
            List<List<Double>> embeddings = generateEmbeddings(chunks);

            List<String> ids = new ArrayList<>();
            List<Map<String, String>> metadatas = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                ids.add(documentId + "_chunk_" + i);
                metadatas.add(Map.of("documentId", documentId, "chunkIndex", String.valueOf(i)));
            }

            Map<String, Object> body = Map.of(
                    "ids", ids,
                    "embeddings", embeddings,
                    "documents", chunks,
                    "metadatas", metadatas
            );

            chromaClient.post()
                    .uri("/api/v1/collections/{id}/add", collectionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.info("Stored document '{}' in ChromaDB ({} chunks)", documentId, chunks.size());
        } catch (Exception e) {
            log.error("Failed to store in ChromaDB: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<String> retrieve(String query, int topK) {
        if (collectionId == null) {
            return List.of();
        }

        try {
            List<Double> queryEmbedding = generateEmbedding(query);

            Map<String, Object> body = Map.of(
                    "query_embeddings", List.of(queryEmbedding),
                    "n_results", topK
            );

            String response = chromaClient.post()
                    .uri("/api/v1/collections/{id}/query", collectionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode documents = root.path("documents");

            List<String> results = new ArrayList<>();
            if (documents.isArray() && !documents.isEmpty()) {
                JsonNode firstResult = documents.get(0);
                for (JsonNode doc : firstResult) {
                    results.add(doc.asText());
                }
            }

            log.debug("Retrieved {} chunks from ChromaDB for query", results.size());
            return results;
        } catch (Exception e) {
            log.warn("Failed to retrieve from ChromaDB: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void delete(String documentId) {
        if (collectionId == null) return;

        try {
            Map<String, Object> body = Map.of(
                    "where", Map.of("documentId", documentId)
            );

            chromaClient.post()
                    .uri("/api/v1/collections/{id}/delete", collectionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            log.info("Deleted document '{}' from ChromaDB", documentId);
        } catch (Exception e) {
            log.warn("Failed to delete from ChromaDB: {}", e.getMessage());
        }
    }

    // ---- Internal helpers ----

    private List<Double> generateEmbedding(String text) {
        try {
            Map<String, Object> body = Map.of(
                    "model", embeddingModel,
                    "prompt", text
            );

            String response = ollamaClient.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode embedding = root.path("embedding");

            List<Double> result = new ArrayList<>();
            for (JsonNode val : embedding) {
                result.add(val.asDouble());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    private List<List<Double>> generateEmbeddings(List<String> texts) {
        List<List<Double>> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
    }

    /**
     * Chunks text into overlapping segments for better retrieval.
     */
    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > chunkSize && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                // Keep overlap
                String overlapText = current.substring(
                        Math.max(0, current.length() - overlap));
                current = new StringBuilder(overlapText);
            }
            current.append(sentence).append(" ");
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }
}
