package com.agenthub.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

/**
 * Request for streaming agent execution via SSE.
 */
public record StreamRequest(
        @NotBlank(message = "Agent name is required")
        String agentName,

        @NotBlank(message = "Input is required")
        String input,

        UUID sessionId,

        Map<String, Object> parameters,

        Map<String, String> context
) {
    public StreamRequest {
        if (parameters == null) parameters = Map.of();
        if (context == null) context = Map.of();
    }
}
