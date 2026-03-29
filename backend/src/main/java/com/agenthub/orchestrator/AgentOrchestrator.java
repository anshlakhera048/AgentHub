package com.agenthub.orchestrator;

import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;
import com.agenthub.orchestrator.dag.DAG;
import com.agenthub.orchestrator.dag.DAGExecutionResult;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrator responsible for routing requests to agents,
 * chaining multiple agents, DAG execution, and streaming workflows.
 */
public interface AgentOrchestrator {

    AgentResponse execute(AgentRequest request);

    AgentResponse executeChain(AgentRequest request);

    CompletableFuture<AgentResponse> executeAsync(AgentRequest request);

    DAGExecutionResult executeWorkflow(DAG dag, String input, UUID sessionId, Map<String, String> context);

    Flux<String> executeStream(AgentRequest request);
}
