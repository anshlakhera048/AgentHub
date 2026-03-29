package com.agenthub.memory.vector;

public record VectorSearchResult(
        String id,
        String content,
        float score
) {
}
