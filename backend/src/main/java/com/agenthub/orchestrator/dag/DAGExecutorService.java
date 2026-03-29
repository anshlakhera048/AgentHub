package com.agenthub.orchestrator.dag;

import com.agenthub.agent.Agent;
import com.agenthub.agent.AgentRegistry;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Executes a DAG of agents with parallel execution of independent nodes.
 * Nodes whose dependencies have all completed are dispatched concurrently.
 */
@Service
public class DAGExecutorService {

    private static final Logger log = LoggerFactory.getLogger(DAGExecutorService.class);

    private final AgentRegistry agentRegistry;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executorService;
    private final int nodeTimeoutSeconds;

    public DAGExecutorService(
            AgentRegistry agentRegistry,
            MeterRegistry meterRegistry,
            @Value("${agenthub.execution.thread-pool-size:8}") int threadPoolSize,
            @Value("${agenthub.execution.node-timeout-seconds:120}") int nodeTimeoutSeconds
    ) {
        this.agentRegistry = agentRegistry;
        this.meterRegistry = meterRegistry;
        this.nodeTimeoutSeconds = nodeTimeoutSeconds;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r);
            t.setName("dag-exec-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Execute the DAG, feeding input to root nodes and flowing outputs to dependents.
     *
     * @param dag       the directed acyclic graph to execute
     * @param input     the initial input for root nodes
     * @param sessionId optional session for memory context
     * @param context   optional context map
     * @return aggregated results for every node
     */
    public DAGExecutionResult execute(DAG dag, String input, UUID sessionId, Map<String, String> context) {
        long startTime = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();

        MDC.put("dagExecutionId", executionId);
        log.info("Starting DAG execution with {} nodes", dag.size());

        Timer.Sample dagTimer = Timer.start(meterRegistry);
        ConcurrentMap<String, DAGNodeResult> results = new ConcurrentHashMap<>();
        ConcurrentMap<String, CompletableFuture<DAGNodeResult>> futures = new ConcurrentHashMap<>();

        try {
            // Schedule all nodes, respecting dependencies
            for (String nodeId : dag.topologicalOrder()) {
                DAGNode node = dag.getNode(nodeId);
                CompletableFuture<DAGNodeResult> future = scheduleNode(
                        node, dag, input, sessionId, context, futures, results, executionId);
                futures.put(nodeId, future);
            }

            // Wait for all nodes to complete
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(nodeTimeoutSeconds * (long) dag.size(), TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            log.error("DAG execution timed out after {}s", nodeTimeoutSeconds * dag.size());
            return buildResult(results, startTime, "DAG execution timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("DAG execution interrupted");
            return buildResult(results, startTime, "DAG execution interrupted");
        } catch (ExecutionException e) {
            log.error("DAG execution failed: {}", e.getCause().getMessage());
            return buildResult(results, startTime, "DAG execution failed: " + e.getCause().getMessage());
        } finally {
            dagTimer.stop(Timer.builder("dag.execution.duration")
                    .tag("node_count", String.valueOf(dag.size()))
                    .register(meterRegistry));
            MDC.remove("dagExecutionId");
        }

        long totalLatency = System.currentTimeMillis() - startTime;
        boolean allSuccess = results.values().stream().allMatch(DAGNodeResult::success);

        log.info("DAG execution completed in {}ms, success={}, nodes={}", totalLatency, allSuccess, dag.size());

        if (allSuccess) {
            return DAGExecutionResult.success(new LinkedHashMap<>(results), totalLatency);
        } else {
            String failedNodes = results.entrySet().stream()
                    .filter(e -> !e.getValue().success())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining(", "));
            return DAGExecutionResult.failure(new LinkedHashMap<>(results), totalLatency,
                    "Failed nodes: " + failedNodes);
        }
    }

    private CompletableFuture<DAGNodeResult> scheduleNode(
            DAGNode node, DAG dag, String rootInput,
            UUID sessionId, Map<String, String> context,
            ConcurrentMap<String, CompletableFuture<DAGNodeResult>> futures,
            ConcurrentMap<String, DAGNodeResult> results,
            String executionId
    ) {
        // Collect dependency futures
        List<CompletableFuture<DAGNodeResult>> depFutures = node.dependencies().stream()
                .map(futures::get)
                .filter(Objects::nonNull)
                .toList();

        CompletableFuture<Void> allDeps = depFutures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(depFutures.toArray(new CompletableFuture[0]));

        return allDeps.thenComposeAsync(ignored -> {
            // Check if any dependency failed
            for (String depId : node.dependencies()) {
                DAGNodeResult depResult = results.get(depId);
                if (depResult != null && !depResult.success()) {
                    DAGNodeResult skipped = DAGNodeResult.failure(
                            node.id(), node.agentName(),
                            "Skipped: dependency '" + depId + "' failed", 0);
                    results.put(node.id(), skipped);
                    return CompletableFuture.completedFuture(skipped);
                }
            }

            // Build input: combine root input with dependency outputs
            String nodeInput = buildNodeInput(node, rootInput, results);

            return CompletableFuture.supplyAsync(() -> {
                MDC.put("dagExecutionId", executionId);
                MDC.put("dagNodeId", node.id());
                try {
                    return executeNode(node, nodeInput, sessionId, context);
                } finally {
                    MDC.remove("dagNodeId");
                    MDC.remove("dagExecutionId");
                }
            }, executorService);
        }, executorService).whenComplete((result, ex) -> {
            if (ex != null) {
                DAGNodeResult failure = DAGNodeResult.failure(
                        node.id(), node.agentName(), ex.getMessage(), 0);
                results.put(node.id(), failure);
            } else if (result != null) {
                results.put(node.id(), result);
            }
        });
    }

    private DAGNodeResult executeNode(DAGNode node, String input, UUID sessionId, Map<String, String> context) {
        Timer.Sample nodeTimer = Timer.start(meterRegistry);
        long start = System.currentTimeMillis();

        log.info("Executing DAG node '{}' with agent '{}'", node.id(), node.agentName());

        try {
            Agent agent = agentRegistry.requireAgent(node.agentName());

            Map<String, Object> mergedParams = new HashMap<>(node.parameters());

            AgentRequest request = new AgentRequest(
                    node.agentName(), input, sessionId,
                    mergedParams, null, context != null ? context : Map.of()
            );

            AgentResponse response = agent.execute(request);
            long latency = System.currentTimeMillis() - start;

            nodeTimer.stop(Timer.builder("dag.node.duration")
                    .tag("node_id", node.id())
                    .tag("agent", node.agentName())
                    .tag("status", response.success() ? "success" : "error")
                    .register(meterRegistry));

            if (response.success()) {
                log.info("DAG node '{}' completed in {}ms", node.id(), latency);
                return DAGNodeResult.success(node.id(), node.agentName(), response.output(), latency);
            } else {
                log.warn("DAG node '{}' failed: {}", node.id(), response.errorMessage());
                return DAGNodeResult.failure(node.id(), node.agentName(), response.errorMessage(), latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            nodeTimer.stop(Timer.builder("dag.node.duration")
                    .tag("node_id", node.id())
                    .tag("agent", node.agentName())
                    .tag("status", "error")
                    .register(meterRegistry));
            log.error("DAG node '{}' threw exception: {}", node.id(), e.getMessage(), e);
            return DAGNodeResult.failure(node.id(), node.agentName(), e.getMessage(), latency);
        }
    }

    private String buildNodeInput(DAGNode node, String rootInput, ConcurrentMap<String, DAGNodeResult> results) {
        if (node.isRoot()) {
            return rootInput;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Original input:\n").append(rootInput).append("\n\n");

        for (String depId : node.dependencies()) {
            DAGNodeResult depResult = results.get(depId);
            if (depResult != null && depResult.success() && depResult.output() != null) {
                sb.append("Output from '").append(depId).append("':\n");
                sb.append(depResult.output()).append("\n\n");
            }
        }

        return sb.toString().trim();
    }

    private DAGExecutionResult buildResult(ConcurrentMap<String, DAGNodeResult> results,
                                            long startTime, String errorMessage) {
        long totalLatency = System.currentTimeMillis() - startTime;
        return DAGExecutionResult.failure(new LinkedHashMap<>(results), totalLatency, errorMessage);
    }
}
