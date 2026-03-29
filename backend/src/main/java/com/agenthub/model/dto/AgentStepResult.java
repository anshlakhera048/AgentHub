package com.agenthub.model.dto;

public record AgentStepResult(
        String agentName,
        String output,
        long latencyMs,
        boolean success,
        String errorMessage
) {
    public static AgentStepResult success(String agentName, String output, long latencyMs) {
        return new AgentStepResult(agentName, output, latencyMs, true, null);
    }

    public static AgentStepResult failure(String agentName, String errorMessage) {
        return new AgentStepResult(agentName, null, 0, false, errorMessage);
    }
}
