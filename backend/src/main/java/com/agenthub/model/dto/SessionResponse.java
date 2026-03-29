package com.agenthub.model.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String userId,
        String agentName,
        Instant createdAt
) {}
