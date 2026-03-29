package com.agenthub.orchestrator.dag;

/**
 * Result of executing a single DAG node.
 */
public record DAGNodeResult(
        String nodeId,
        String agentName,
        String output,
        long latencyMs,
        boolean success,
        String errorMessage
) {
    public static DAGNodeResult success(String nodeId, String agentName, String output, long latencyMs) {
        return new DAGNodeResult(nodeId, agentName, output, latencyMs, true, null);
    }

    public static DAGNodeResult failure(String nodeId, String agentName, String errorMessage, long latencyMs) {
        return new DAGNodeResult(nodeId, agentName, null, latencyMs, false, errorMessage);
    }
}
