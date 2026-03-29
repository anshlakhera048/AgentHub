package com.agenthub.model.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeIngestRequest(
        @NotBlank String content,
        String documentId
) {
}
