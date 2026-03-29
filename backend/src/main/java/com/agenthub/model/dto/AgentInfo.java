package com.agenthub.model.dto;

import java.util.UUID;

public record AgentInfo(
        UUID id,
        String name,
        String description,
        boolean enabled
) {}
