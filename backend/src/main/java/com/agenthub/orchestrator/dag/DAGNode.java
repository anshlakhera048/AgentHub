package com.agenthub.orchestrator.dag;

import java.util.Map;
import java.util.Set;

/**
 * Represents a single node in a DAG workflow.
 *
 * @param id           unique identifier for this node
 * @param agentName    agent to execute at this node
 * @param dependencies set of node IDs that must complete before this node runs
 * @param parameters   additional parameters to pass to the agent
 */
public record DAGNode(
        String id,
        String agentName,
        Set<String> dependencies,
        Map<String, Object> parameters
) {
    public DAGNode {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Node id must not be blank");
        if (agentName == null || agentName.isBlank()) throw new IllegalArgumentException("Agent name must not be blank");
        if (dependencies == null) dependencies = Set.of();
        if (parameters == null) parameters = Map.of();
    }

    public boolean isRoot() {
        return dependencies.isEmpty();
    }
}
