package com.agenthub.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentResponse(
        UUID requestId,
        String agentName,
        String output,
        long latencyMs,
        boolean success,
        String errorMessage,
        List<AgentStepResult> chainResults,
        Map<String, Object> metadata,
        Instant timestamp
) {

    public static AgentResponse success(String agentName, String output, long latencyMs) {
        return new AgentResponse(
                UUID.randomUUID(), agentName, output, latencyMs,
                true, null, List.of(), Map.of(), Instant.now()
        );
    }

    public static AgentResponse success(String agentName, String output, long latencyMs,
                                         List<AgentStepResult> chainResults) {
        return new AgentResponse(
                UUID.randomUUID(), agentName, output, latencyMs,
                true, null, chainResults, Map.of(), Instant.now()
        );
    }

    public static AgentResponse failure(String agentName, String errorMessage) {
        return new AgentResponse(
                UUID.randomUUID(), agentName, null, 0,
                false, errorMessage, List.of(), Map.of(), Instant.now()
        );
    }
}
