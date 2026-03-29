package com.agenthub.memory.vector;

import java.util.List;

/**
 * Abstraction over a vector database for storing and searching embeddings.
 * Implementations may be backed by an in-memory store, ChromaDB, FAISS, etc.
 */
public interface VectorStore {

    void store(String id, String content, float[] embedding);

    List<VectorSearchResult> search(float[] queryEmbedding, int topK);

    void delete(String id);
}
