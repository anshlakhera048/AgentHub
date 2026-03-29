package com.agenthub.model.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        String role,
        String content,
        String agentName,
        Long latencyMs,
        Instant timestamp
) {}
