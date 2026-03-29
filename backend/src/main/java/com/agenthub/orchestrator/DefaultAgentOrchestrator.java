package com.agenthub.orchestrator;

import com.agenthub.agent.Agent;
import com.agenthub.agent.AgentRegistry;
import com.agenthub.llm.LLMClient;
import com.agenthub.memory.MemoryService;
import com.agenthub.model.dto.*;
import com.agenthub.orchestrator.dag.DAG;
import com.agenthub.orchestrator.dag.DAGExecutionResult;
import com.agenthub.orchestrator.dag.DAGExecutorService;
import com.agenthub.prompt.PromptEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class DefaultAgentOrchestrator implements AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentOrchestrator.class);
    private static final int MAX_CHAIN_DEPTH = 10;

    private final AgentRegistry agentRegistry;
    private final MeterRegistry meterRegistry;
    private final DAGExecutorService dagExecutorService;
    private final LLMClient llmClient;
    private final PromptEngine promptEngine;
    private final MemoryService memoryService;

    public DefaultAgentOrchestrator(AgentRegistry agentRegistry, MeterRegistry meterRegistry,
                                     DAGExecutorService dagExecutorService,
                                     LLMClient llmClient, PromptEngine promptEngine,
                                     MemoryService memoryService) {
        this.agentRegistry = agentRegistry;
        this.meterRegistry = meterRegistry;
        this.dagExecutorService = dagExecutorService;
        this.llmClient = llmClient;
        this.promptEngine = promptEngine;
        this.memoryService = memoryService;
    }

    @Override
    public AgentResponse execute(AgentRequest request) {
        log.info("Orchestrator routing request to agent: {}", request.agentName());

        // If chain agents are specified, run a sequential chain
        if (request.chainAgents() != null && !request.chainAgents().isEmpty()) {
            return executeChain(request);
        }

        return executeSingleAgent(request);
    }

    @Override
    public AgentResponse executeChain(AgentRequest request) {
        List<String> chain = new ArrayList<>();
        chain.add(request.agentName());
        if (request.chainAgents() != null) {
            chain.addAll(request.chainAgents());
        }

        if (chain.size() > MAX_CHAIN_DEPTH) {
            return AgentResponse.failure(request.agentName(),
                    "Chain exceeds maximum depth of " + MAX_CHAIN_DEPTH);
        }

        log.info("Executing agent chain: {}", chain);
        Timer.Sample totalSample = Timer.start(meterRegistry);

        List<AgentStepResult> stepResults = new ArrayList<>();
        String currentInput = request.input();
        String lastOutput = null;

        for (int i = 0; i < chain.size(); i++) {
            String agentName = chain.get(i);
            log.info("Chain step {}/{}: agent '{}'", i + 1, chain.size(), agentName);

            Agent agent = agentRegistry.requireAgent(agentName);

            // Build request for this step — pass previous output as input
            AgentRequest stepRequest = new AgentRequest(
                    agentName,
                    currentInput,
                    request.sessionId(),
                    request.parameters(),
                    null, // no sub-chaining
                    request.context()
            );

            AgentResponse stepResponse = agent.execute(stepRequest);
            stepResults.add(new AgentStepResult(
                    agentName, stepResponse.output(),
                    stepResponse.latencyMs(), stepResponse.success(),
                    stepResponse.errorMessage()
            ));

            if (!stepResponse.success()) {
                log.warn("Chain aborted at step {} due to agent '{}' failure: {}",
                        i + 1, agentName, stepResponse.errorMessage());
                long totalLatency = stepResults.stream().mapToLong(AgentStepResult::latencyMs).sum();
                return AgentResponse.failure(request.agentName(),
                        "Chain failed at step " + (i + 1) + " (" + agentName + "): " +
                                stepResponse.errorMessage());
            }

            lastOutput = stepResponse.output();
            // Feed output of current agent as input to next agent
            currentInput = lastOutput;
        }

        long totalLatency = stepResults.stream().mapToLong(AgentStepResult::latencyMs).sum();
        totalSample.stop(Timer.builder("orchestrator.chain.duration")
                .tag("chain_length", String.valueOf(chain.size()))
                .register(meterRegistry));

        log.info("Chain completed successfully in {}ms with {} steps", totalLatency, chain.size());
        return AgentResponse.success(request.agentName(), lastOutput, totalLatency, stepResults);
    }

    @Override
    @Async("agentExecutor")
    public CompletableFuture<AgentResponse> executeAsync(AgentRequest request) {
        return CompletableFuture.completedFuture(execute(request));
    }

    @Override
    public DAGExecutionResult executeWorkflow(DAG dag, String input, UUID sessionId, Map<String, String> context) {
        log.info("[Orchestrator] Executing DAG workflow with {} nodes", dag.size());
        Timer.Sample sample = Timer.start(meterRegistry);

        DAGExecutionResult result = dagExecutorService.execute(dag, input, sessionId, context);

        sample.stop(Timer.builder("orchestrator.workflow.duration")
                .tag("node_count", String.valueOf(dag.size()))
                .tag("status", result.success() ? "success" : "error")
                .register(meterRegistry));

        meterRegistry.counter("orchestrator.workflow.total",
                "status", result.success() ? "success" : "error"
        ).increment();

        log.info("[Orchestrator] DAG workflow completed in {}ms, success={}",
                result.totalLatencyMs(), result.success());
        return result;
    }

    @Override
    public Flux<String> executeStream(AgentRequest request) {
        log.info("[Orchestrator] Streaming execution for agent='{}'", request.agentName());

        Agent agent = agentRegistry.requireAgent(request.agentName());

        // Build the prompt the same way the agent would
        String memoryContext = "";
        if (request.sessionId() != null) {
            try {
                String shortTerm = memoryService.getShortTermMemory(request.sessionId().toString());
                String longTerm = memoryService.retrieveRelevantContext(request.input(), 3);
                StringBuilder sb = new StringBuilder();
                if (shortTerm != null && !shortTerm.isBlank()) {
                    sb.append("Recent conversation:\n").append(shortTerm).append("\n\n");
                }
                if (longTerm != null && !longTerm.isBlank()) {
                    sb.append("Relevant knowledge:\n").append(longTerm).append("\n\n");
                }
                memoryContext = sb.toString();
            } catch (Exception e) {
                log.warn("Failed to retrieve memory context for streaming: {}", e.getMessage());
            }
        }

        // Build prompt using the agent's template
        String template = promptEngine.getTemplate(agent.getName());
        Map<String, String> vars = new HashMap<>();
        vars.put("user_input", request.input());
        vars.put("memory_context", memoryContext);
        if (request.context() != null) {
            vars.putAll(request.context());
        }
        String prompt = promptEngine.render(template, vars);

        return llmClient.stream(prompt)
                .doOnSubscribe(s -> log.info("[Orchestrator] Stream started for agent='{}'", request.agentName()))
                .doOnComplete(() -> {
                    log.info("[Orchestrator] Stream completed for agent='{}'", request.agentName());
                    // Store interaction in memory
                    if (request.sessionId() != null) {
                        try {
                            memoryService.storeShortTermMemory(
                                    request.sessionId().toString(),
                                    "User: " + request.input());
                        } catch (Exception e) {
                            log.warn("Failed to store streaming interaction in memory: {}", e.getMessage());
                        }
                    }
                })
                .doOnError(e -> log.error("[Orchestrator] Stream error for agent='{}': {}",
                        request.agentName(), e.getMessage()));
    }

    private AgentResponse executeSingleAgent(AgentRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("[Orchestrator] Dispatching to agent='{}', sessionId={}, hasChain=false",
                request.agentName(),
                request.sessionId() != null ? request.sessionId() : "none");

        Agent agent = agentRegistry.requireAgent(request.agentName());
        AgentResponse response = agent.execute(request);

        sample.stop(Timer.builder("orchestrator.execute.duration")
                .tag("agent", request.agentName())
                .tag("status", response.success() ? "success" : "error")
                .register(meterRegistry));

        meterRegistry.counter("orchestrator.execute.total",
                "agent", request.agentName(),
                "status", response.success() ? "success" : "error"
        ).increment();

        log.info("[Orchestrator] Agent='{}' finished in {}ms, success={}",
                request.agentName(), response.latencyMs(), response.success());

        return response;
    }
}
