package com.agenthub.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.Set;

/**
 * Defines a single node within a workflow DAG.
 */
public record WorkflowNodeDefinition(
        @NotBlank(message = "Node id is required")
        String id,

        @NotBlank(message = "Agent name is required")
        String agentName,

        Set<String> dependencies,

        Map<String, Object> parameters
) {
    public WorkflowNodeDefinition {
        if (dependencies == null) dependencies = Set.of();
        if (parameters == null) parameters = Map.of();
    }
}
