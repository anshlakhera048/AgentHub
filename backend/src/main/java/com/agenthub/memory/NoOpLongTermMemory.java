package com.agenthub.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * No-op long-term memory fallback. Used when no vector store or ChromaDB is configured.
 * All operations are silently ignored — no vector storage or retrieval.
 * Registered via {@link com.agenthub.config.MemoryAutoConfiguration}.
 */
public class NoOpLongTermMemory implements LongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(NoOpLongTermMemory.class);

    public NoOpLongTermMemory() {
        log.info("Using no-op long-term memory (ChromaDB not configured)");
    }

    @Override
    public void store(String content, String documentId) {
        log.debug("NoOp: skipping store for document '{}'", documentId);
    }

    @Override
    public List<String> retrieve(String query, int topK) {
        return List.of();
    }

    @Override
    public void delete(String documentId) {
        log.debug("NoOp: skipping delete for document '{}'", documentId);
    }
}
