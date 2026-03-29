package com.agenthub.model.dto;

import com.agenthub.orchestrator.dag.DAGNodeResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response from executing a DAG workflow.
 */
public record WorkflowResponse(
        UUID executionId,
        boolean success,
        long totalLatencyMs,
        Map<String, DAGNodeResult> nodeResults,
        String errorMessage,
        Instant timestamp
) {
}
