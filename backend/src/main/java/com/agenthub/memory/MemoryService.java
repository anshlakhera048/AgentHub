package com.agenthub.memory;

import java.util.List;

/**
 * Unified memory service providing both short-term (Redis) and
 * long-term (Vector DB) memory capabilities.
 */
public interface MemoryService {

    // --- Short-term memory (Redis-backed) ---

    void storeShortTermMemory(String sessionId, String content);

    String getShortTermMemory(String sessionId);

    void clearShortTermMemory(String sessionId);

    // --- Long-term memory (Vector DB-backed) ---

    void storeDocument(String content, String documentId);

    String retrieveRelevantContext(String query, int topK);

    List<String> retrieveRelevantChunks(String query, int topK);

    void deleteDocument(String documentId);
}
