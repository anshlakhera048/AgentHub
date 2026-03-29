package com.agenthub.model.dto;

import jakarta.validation.constraints.NotBlank;

public record SessionCreateRequest(
        String userId,
        @NotBlank(message = "Agent name is required")
        String agentName
) {}
