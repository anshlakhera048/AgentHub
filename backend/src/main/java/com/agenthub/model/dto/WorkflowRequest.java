package com.agenthub.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request to execute a DAG workflow.
 */
public record WorkflowRequest(
        @NotBlank(message = "Input is required")
        String input,

        @NotEmpty(message = "At least one node is required")
        @Valid
        List<WorkflowNodeDefinition> nodes,

        UUID sessionId,

        Map<String, String> context
) {
    public WorkflowRequest {
        if (context == null) context = Map.of();
    }
}
