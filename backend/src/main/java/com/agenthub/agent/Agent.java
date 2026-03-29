package com.agenthub.agent;

import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;

/**
 * Core agent abstraction. Every agent in the system must implement this interface.
 */
public interface Agent {

    String getName();

    String getDescription();

    AgentResponse execute(AgentRequest request);
}
