package com.agenthub.orchestrator.dag;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Result of executing an entire DAG workflow.
 */
public record DAGExecutionResult(
        UUID executionId,
        boolean success,
        long totalLatencyMs,
        Map<String, DAGNodeResult> nodeResults,
        String errorMessage,
        Instant timestamp
) {
    public static DAGExecutionResult success(Map<String, DAGNodeResult> nodeResults, long totalLatencyMs) {
        return new DAGExecutionResult(
                UUID.randomUUID(), true, totalLatencyMs, nodeResults, null, Instant.now());
    }

    public static DAGExecutionResult failure(Map<String, DAGNodeResult> nodeResults,
                                              long totalLatencyMs, String errorMessage) {
        return new DAGExecutionResult(
                UUID.randomUUID(), false, totalLatencyMs, nodeResults, errorMessage, Instant.now());
    }
}
