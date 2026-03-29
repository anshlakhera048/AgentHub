package com.agenthub.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentRequest(
        @NotBlank(message = "Agent name is required")
        String agentName,

        @NotBlank(message = "Input is required")
        String input,

        UUID sessionId,

        Map<String, Object> parameters,

        List<String> chainAgents,

        Map<String, String> context
) {
    public AgentRequest {
        if (parameters == null) parameters = Map.of();
        if (context == null) context = Map.of();
    }
}
