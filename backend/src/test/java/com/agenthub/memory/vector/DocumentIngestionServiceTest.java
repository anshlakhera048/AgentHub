package com.agenthub.memory.vector;

import com.agenthub.memory.embedding.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentIngestionServiceTest {

    private DocumentIngestionService ingestionService;
    private InMemoryVectorStore vectorStore;
    private StubEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore();
        embeddingService = new StubEmbeddingService();
        ingestionService = new DocumentIngestionService(vectorStore, embeddingService, 500, 50);
    }

    @Test
    void ingestShortTextStoresOneChunk() {
        int stored = ingestionService.ingest("Short text content.", "doc1");
        assertEquals(1, stored);
        assertEquals(1, vectorStore.size());
    }

    @Test
    void ingestLongTextStoresMultipleChunks() {
        // Create text longer than chunk size
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("This is sentence number ").append(i).append(" which adds content to the document. ");
        }
        int stored = ingestionService.ingest(sb.toString(), "doc-long");
        assertTrue(stored > 1, "Long text should be split into multiple chunks");
        assertEquals(stored, vectorStore.size());
    }

    @Test
    void ingestEmptyContentReturnsZero() {
        int stored = ingestionService.ingest("", "doc1");
        assertEquals(0, stored);
    }

    @Test
    void ingestNullContentReturnsZero() {
        int stored = ingestionService.ingest(null, "doc1");
        assertEquals(0, stored);
    }

    @Test
    void ingestWithNullDocumentIdGeneratesId() {
        int stored = ingestionService.ingest("Some content.", null);
        assertEquals(1, stored);
        assertEquals(1, vectorStore.size());
    }

    @Test
    void deleteRemovesIngestedDocument() {
        ingestionService.ingest("Some content.", "doc1");
        assertEquals(1, vectorStore.size());

        ingestionService.delete("doc1");
        assertEquals(0, vectorStore.size());
    }

    @Test
    void ingestWithNoVectorStoreReturnsZero() {
        DocumentIngestionService noOp = new DocumentIngestionService(null, null, 500, 50);
        int stored = noOp.ingest("content", "doc1");
        assertEquals(0, stored);
    }

    @Test
    void chunkTextSplitsCorrectly() {
        String text = "First sentence. Second sentence. Third sentence. " +
                "Fourth sentence. Fifth sentence. Sixth sentence.";
        // Use small chunk size to force splitting
        List<String> chunks = ingestionService.chunkText(text, 40, 10);
        assertTrue(chunks.size() > 1);
        // All original content should be represented
        String joined = String.join(" ", chunks);
        assertTrue(joined.contains("First sentence"));
        assertTrue(joined.contains("Sixth sentence"));
    }

    @Test
    void chunkTextKeepsShortTextAsOneChunk() {
        List<String> chunks = ingestionService.chunkText("Short.", 500, 50);
        assertEquals(1, chunks.size());
        assertEquals("Short.", chunks.get(0));
    }

    // --- Stub EmbeddingService ---

    private static class StubEmbeddingService implements EmbeddingService {
        @Override
        public float[] embed(String text) {
            // Return a deterministic pseudo-embedding based on text hash
            int hash = text.hashCode();
            return new float[]{
                    (float) Math.sin(hash),
                    (float) Math.cos(hash),
                    (float) Math.sin(hash * 2)
            };
        }
    }
}
