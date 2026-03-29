package com.agenthub.memory;

import java.util.List;

/**
 * Long-term memory interface backed by a vector database.
 * Handles embedding generation, storage, and semantic retrieval.
 */
public interface LongTermMemory {

    void store(String content, String documentId);

    List<String> retrieve(String query, int topK);

    void delete(String documentId);
}
