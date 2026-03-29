package com.agenthub.memory.vector;

import com.agenthub.memory.embedding.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticSearchServiceTest {

    private SemanticSearchService searchService;
    private InMemoryVectorStore vectorStore;
    private StubEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore();
        embeddingService = new StubEmbeddingService();
        searchService = new SemanticSearchService(vectorStore, embeddingService, 5);
    }

    @Test
    void searchReturnsMatchingResults() {
        // Store some vectors
        vectorStore.store("chunk1", "Java is a programming language", new float[]{1.0f, 0.0f, 0.0f});
        vectorStore.store("chunk2", "Python is great for ML", new float[]{0.0f, 1.0f, 0.0f});
        vectorStore.store("chunk3", "Spring Boot uses Java", new float[]{0.9f, 0.1f, 0.0f});

        // Stub returns an embedding close to Java vectors
        embeddingService.setEmbedding(new float[]{0.95f, 0.05f, 0.0f});

        List<VectorSearchResult> results = searchService.search("What is Java?", 2);

        assertEquals(2, results.size());
        assertEquals("chunk1", results.get(0).id());
    }

    @Test
    void searchWithEmptyQueryReturnsEmptyList() {
        List<VectorSearchResult> results = searchService.search("", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchWithNullQueryReturnsEmptyList() {
        List<VectorSearchResult> results = searchService.search(null, 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchWithNoVectorStoreReturnsEmptyList() {
        SemanticSearchService noOp = new SemanticSearchService(null, null, 5);
        List<VectorSearchResult> results = noOp.search("query", 3);
        assertTrue(results.isEmpty());
    }

    @Test
    void isAvailableReturnsTrueWhenConfigured() {
        assertTrue(searchService.isAvailable());
    }

    @Test
    void isAvailableReturnsFalseWhenNotConfigured() {
        SemanticSearchService noOp = new SemanticSearchService(null, null, 5);
        assertFalse(noOp.isAvailable());
    }

    @Test
    void defaultTopKIsUsed() {
        for (int i = 0; i < 10; i++) {
            vectorStore.store("doc" + i, "content " + i,
                    new float[]{(float) Math.random(), (float) Math.random(), (float) Math.random()});
        }
        embeddingService.setEmbedding(new float[]{0.5f, 0.5f, 0.5f});

        // Default topK is 5
        List<VectorSearchResult> results = searchService.search("query");
        assertEquals(5, results.size());
    }

    // --- Stub EmbeddingService ---

    private static class StubEmbeddingService implements EmbeddingService {
        private float[] embedding = {0.5f, 0.5f, 0.5f};

        void setEmbedding(float[] e) { this.embedding = e; }

        @Override
        public float[] embed(String text) {
            return embedding;
        }
    }
}
