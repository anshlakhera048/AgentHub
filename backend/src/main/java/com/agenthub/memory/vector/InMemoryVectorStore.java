package com.agenthub.memory.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store using cosine similarity for search.
 * Suitable for development and testing — data is lost on restart.
 */
@Component
@ConditionalOnProperty(name = "agenthub.memory.vector.enabled", havingValue = "true")
public class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final Map<String, StoredEntry> entries = new ConcurrentHashMap<>();

    public InMemoryVectorStore() {
        log.info("Initialized InMemoryVectorStore");
    }

    @Override
    public void store(String id, String content, float[] embedding) {
        entries.put(id, new StoredEntry(id, content, embedding));
        log.debug("Stored vector entry '{}', total entries: {}", id, entries.size());
    }

    @Override
    public List<VectorSearchResult> search(float[] queryEmbedding, int topK) {
        if (entries.isEmpty()) {
            return List.of();
        }

        return entries.values().stream()
                .map(entry -> new VectorSearchResult(
                        entry.id, entry.content,
                        cosineSimilarity(queryEmbedding, entry.embedding)))
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        int removed = 0;
        Iterator<String> it = entries.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (key.equals(id) || key.startsWith(id + "_chunk_")) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Deleted {} entries for id '{}'", removed, id);
        }
    }

    public int size() {
        return entries.size();
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0f;

        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0f || normB == 0f) return 0f;
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private record StoredEntry(String id, String content, float[] embedding) {
    }
}
