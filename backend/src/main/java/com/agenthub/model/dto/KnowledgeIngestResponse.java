package com.agenthub.model.dto;

public record KnowledgeIngestResponse(
        String documentId,
        int chunksStored,
        String status
) {
    public static KnowledgeIngestResponse success(String documentId, int chunksStored) {
        return new KnowledgeIngestResponse(documentId, chunksStored, "success");
    }

    public static KnowledgeIngestResponse failure(String documentId, String reason) {
        return new KnowledgeIngestResponse(documentId, 0, "failed: " + reason);
    }
}
