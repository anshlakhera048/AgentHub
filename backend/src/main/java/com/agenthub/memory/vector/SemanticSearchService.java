package com.agenthub.memory.vector;

import com.agenthub.memory.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Performs semantic search: embeds a query and searches the VectorStore for matching chunks.
 */
@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final int defaultTopK;

    public SemanticSearchService(
            @Autowired(required = false) VectorStore vectorStore,
            @Autowired(required = false) EmbeddingService embeddingService,
            @Value("${agenthub.memory.retrieval.default-top-k:5}") int defaultTopK
    ) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.defaultTopK = defaultTopK;

        if (vectorStore == null || embeddingService == null) {
            log.info("SemanticSearchService initialized in no-op mode (VectorStore or EmbeddingService not available)");
        } else {
            log.info("SemanticSearchService initialized with defaultTopK={}", defaultTopK);
        }
    }

    public List<VectorSearchResult> search(String query, int topK) {
        if (vectorStore == null || embeddingService == null) {
            return List.of();
        }

        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            float[] queryEmbedding = embeddingService.embed(query);
            List<VectorSearchResult> results = vectorStore.search(queryEmbedding, topK);
            log.debug("Semantic search for '{}' returned {} results (topK={})",
                    truncate(query, 80), results.size(), topK);
            return results;
        } catch (Exception e) {
            log.warn("Semantic search failed for query '{}': {}", truncate(query, 80), e.getMessage());
            return List.of();
        }
    }

    public List<VectorSearchResult> search(String query) {
        return search(query, defaultTopK);
    }

    public boolean isAvailable() {
        return vectorStore != null && embeddingService != null;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
