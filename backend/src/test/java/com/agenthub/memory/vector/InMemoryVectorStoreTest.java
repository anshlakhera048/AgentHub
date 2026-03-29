package com.agenthub.memory.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryVectorStoreTest {

    private InMemoryVectorStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore();
    }

    @Test
    void storeAndSearchReturnsBestMatch() {
        // Store two vectors: one about "cats" and one about "dogs"
        float[] catVector = {1.0f, 0.0f, 0.0f};
        float[] dogVector = {0.0f, 1.0f, 0.0f};
        store.store("doc1", "Cats are great pets", catVector);
        store.store("doc2", "Dogs are loyal animals", dogVector);

        // Query closer to "cats" vector
        float[] query = {0.9f, 0.1f, 0.0f};
        List<VectorSearchResult> results = store.search(query, 2);

        assertEquals(2, results.size());
        assertEquals("doc1", results.get(0).id(), "Cat doc should be the top match");
        assertTrue(results.get(0).score() > results.get(1).score());
    }

    @Test
    void searchEmptyStoreReturnsEmptyList() {
        float[] query = {1.0f, 0.0f};
        List<VectorSearchResult> results = store.search(query, 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchRespectsTopK() {
        for (int i = 0; i < 10; i++) {
            store.store("doc" + i, "Content " + i, new float[]{(float) Math.random(), (float) Math.random()});
        }

        List<VectorSearchResult> results = store.search(new float[]{0.5f, 0.5f}, 3);
        assertEquals(3, results.size());
    }

    @Test
    void deleteRemovesEntry() {
        store.store("doc1", "content", new float[]{1.0f});
        assertEquals(1, store.size());

        store.delete("doc1");
        assertEquals(0, store.size());
    }

    @Test
    void deleteRemovesChunks() {
        store.store("doc1_chunk_0", "chunk 0", new float[]{1.0f});
        store.store("doc1_chunk_1", "chunk 1", new float[]{0.5f});
        store.store("doc2_chunk_0", "other doc", new float[]{0.0f});
        assertEquals(3, store.size());

        store.delete("doc1");
        assertEquals(1, store.size());
    }

    @Test
    void cosineSimilarityIsCorrectForIdenticalVectors() {
        float[] v = {1.0f, 2.0f, 3.0f};
        store.store("doc1", "content", v);

        List<VectorSearchResult> results = store.search(v, 1);
        assertEquals(1, results.size());
        assertEquals(1.0f, results.get(0).score(), 0.001f);
    }

    @Test
    void cosineSimilarityIsCorrectForOrthogonalVectors() {
        store.store("doc1", "content", new float[]{1.0f, 0.0f});
        List<VectorSearchResult> results = store.search(new float[]{0.0f, 1.0f}, 1);
        assertEquals(1, results.size());
        assertEquals(0.0f, results.get(0).score(), 0.001f);
    }
}
