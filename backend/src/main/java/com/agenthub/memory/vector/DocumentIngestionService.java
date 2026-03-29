package com.agenthub.memory.vector;

import com.agenthub.memory.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles document ingestion: chunking text, generating embeddings, and storing in VectorStore.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentIngestionService(
            @Autowired(required = false) VectorStore vectorStore,
            @Autowired(required = false) EmbeddingService embeddingService,
            @Value("${agenthub.memory.ingestion.chunk-size:500}") int chunkSize,
            @Value("${agenthub.memory.ingestion.chunk-overlap:50}") int chunkOverlap
    ) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;

        if (vectorStore == null || embeddingService == null) {
            log.info("DocumentIngestionService initialized in no-op mode (VectorStore or EmbeddingService not available)");
        } else {
            log.info("DocumentIngestionService initialized with chunkSize={}, chunkOverlap={}", chunkSize, chunkOverlap);
        }
    }

    /**
     * Ingest a document: chunk it, embed each chunk, store in vector DB.
     *
     * @return number of chunks stored
     */
    public int ingest(String content, String documentId) {
        if (vectorStore == null || embeddingService == null) {
            log.warn("Cannot ingest document — vector store or embedding service not available");
            return 0;
        }

        if (content == null || content.isBlank()) {
            log.warn("Ignoring empty content for document '{}'", documentId);
            return 0;
        }

        if (documentId == null || documentId.isBlank()) {
            documentId = UUID.randomUUID().toString();
        }

        List<String> chunks = chunkText(content, chunkSize, chunkOverlap);
        log.info("Ingesting document '{}': {} chunks (chunkSize={}, overlap={})",
                documentId, chunks.size(), chunkSize, chunkOverlap);

        int stored = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            try {
                float[] embedding = embeddingService.embed(chunk);
                String chunkId = documentId + "_chunk_" + i;
                vectorStore.store(chunkId, chunk, embedding);
                stored++;
            } catch (Exception e) {
                log.warn("Failed to embed/store chunk {} of document '{}': {}",
                        i, documentId, e.getMessage());
            }
        }

        log.info("Successfully stored {}/{} chunks for document '{}'", stored, chunks.size(), documentId);
        return stored;
    }

    public void delete(String documentId) {
        if (vectorStore == null) {
            return;
        }
        vectorStore.delete(documentId);
        log.info("Deleted document '{}' from vector store", documentId);
    }

    /**
     * Chunks text into overlapping segments using sentence boundaries.
     */
    List<String> chunkText(String text, int maxChunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= maxChunkSize) {
            chunks.add(text.trim());
            return chunks;
        }

        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > maxChunkSize && !current.isEmpty()) {
                chunks.add(current.toString().trim());
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
