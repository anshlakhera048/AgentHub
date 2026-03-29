package com.agenthub.memory.embedding;

/**
 * Converts text into vector embeddings.
 * Implementations may target Ollama, sentence-transformers, or other embedding backends.
 */
public interface EmbeddingService {

    float[] embed(String text);
}
