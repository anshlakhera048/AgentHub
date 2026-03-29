package com.agenthub.orchestrator.dag;

import com.agenthub.agent.Agent;
import com.agenthub.agent.AgentRegistry;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DAGExecutorServiceTest {

    private AgentRegistry agentRegistry;
    private MeterRegistry meterRegistry;
    private DAGExecutorService executorService;

    @BeforeEach
    void setUp() {
        agentRegistry = mock(AgentRegistry.class);
        meterRegistry = new SimpleMeterRegistry();
        executorService = new DAGExecutorService(agentRegistry, meterRegistry, 4, 60);
    }

    @Test
    void executeSingleNodeDAG() {
        Agent agent = mockAgent("Agent1", "output1");
        when(agentRegistry.requireAgent("Agent1")).thenReturn(agent);

        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "Agent1", Set.of(), Map.of()))
                .build();

        DAGExecutionResult result = executorService.execute(dag, "hello", null, Map.of());

        assertTrue(result.success());
        assertEquals(1, result.nodeResults().size());
        assertTrue(result.nodeResults().get("a").success());
        assertEquals("output1", result.nodeResults().get("a").output());
    }

    @Test
    void executeLinearChain() {
        Agent agent1 = mockAgent("Agent1", "step1-output");
        Agent agent2 = mockAgent("Agent2", "step2-output");
        when(agentRegistry.requireAgent("Agent1")).thenReturn(agent1);
        when(agentRegistry.requireAgent("Agent2")).thenReturn(agent2);

        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "Agent1", Set.of(), Map.of()))
                .addNode(new DAGNode("b", "Agent2", Set.of("a"), Map.of()))
                .build();

        DAGExecutionResult result = executorService.execute(dag, "input", null, Map.of());

        assertTrue(result.success());
        assertEquals(2, result.nodeResults().size());
        assertEquals("step1-output", result.nodeResults().get("a").output());
        assertEquals("step2-output", result.nodeResults().get("b").output());
    }

    @Test
    void executeParallelNodes() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(2);
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        Agent parallelAgent = mock(Agent.class);
        when(parallelAgent.getName()).thenReturn("ParallelAgent");
        when(parallelAgent.execute(any(AgentRequest.class))).thenAnswer(inv -> {
            int concurrent = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, concurrent));
            startLatch.countDown();
            startLatch.await(); // Wait for both to be running
            Thread.sleep(50); // Brief overlap time
            concurrentCount.decrementAndGet();
            return AgentResponse.success("ParallelAgent", "parallel-output", 50);
        });
        when(agentRegistry.requireAgent("ParallelAgent")).thenReturn(parallelAgent);

        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "ParallelAgent", Set.of(), Map.of()))
                .addNode(new DAGNode("b", "ParallelAgent", Set.of(), Map.of()))
                .build();

        DAGExecutionResult result = executorService.execute(dag, "input", null, Map.of());

        assertTrue(result.success());
        assertEquals(2, result.nodeResults().size());
        // Both nodes ran concurrently
        assertEquals(2, maxConcurrent.get(), "Both nodes should have run in parallel");
    }

    @Test
    void dependencyFailurePropagates() {
        Agent failAgent = mock(Agent.class);
        when(failAgent.getName()).thenReturn("FailAgent");
        when(failAgent.execute(any(AgentRequest.class)))
                .thenReturn(AgentResponse.failure("FailAgent", "something broke"));

        Agent successAgent = mockAgent("SuccessAgent", "should-not-run");

        when(agentRegistry.requireAgent("FailAgent")).thenReturn(failAgent);
        when(agentRegistry.requireAgent("SuccessAgent")).thenReturn(successAgent);

        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "FailAgent", Set.of(), Map.of()))
                .addNode(new DAGNode("b", "SuccessAgent", Set.of("a"), Map.of()))
                .build();

        DAGExecutionResult result = executorService.execute(dag, "input", null, Map.of());

        assertFalse(result.success());
        assertFalse(result.nodeResults().get("a").success());
        assertFalse(result.nodeResults().get("b").success());
        assertTrue(result.nodeResults().get("b").errorMessage().contains("dependency"));
    }

    @Test
    void diamondDAGExecution() {
        Agent agentA = mockAgent("A", "a-out");
        Agent agentB = mockAgent("B", "b-out");
        Agent agentC = mockAgent("C", "c-out");
        Agent agentD = mockAgent("D", "d-out");
        when(agentRegistry.requireAgent("A")).thenReturn(agentA);
        when(agentRegistry.requireAgent("B")).thenReturn(agentB);
        when(agentRegistry.requireAgent("C")).thenReturn(agentC);
        when(agentRegistry.requireAgent("D")).thenReturn(agentD);

        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "A", Set.of(), Map.of()))
                .addNode(new DAGNode("b", "B", Set.of("a"), Map.of()))
                .addNode(new DAGNode("c", "C", Set.of("a"), Map.of()))
                .addNode(new DAGNode("d", "D", Set.of("b", "c"), Map.of()))
                .build();

        DAGExecutionResult result = executorService.execute(dag, "input", null, Map.of());

        assertTrue(result.success());
        assertEquals(4, result.nodeResults().size());
        assertTrue(result.nodeResults().values().stream().allMatch(DAGNodeResult::success));
    }

    @Test
    void agentExceptionHandledGracefully() {
        Agent throwingAgent = mock(Agent.class);
        when(throwingAgent.getName()).thenReturn("Thrower");
        when(throwingAgent.execute(any(AgentRequest.class)))
                .thenThrow(new RuntimeException("boom"));
        when(agentRegistry.requireAgent("Thrower")).thenReturn(throwingAgent);

        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "Thrower", Set.of(), Map.of()))
                .build();

        DAGExecutionResult result = executorService.execute(dag, "input", null, Map.of());

        assertFalse(result.success());
        assertFalse(result.nodeResults().get("a").success());
        assertTrue(result.nodeResults().get("a").errorMessage().contains("boom"));
    }

    @Test
    void metricsRecorded() {
        Agent agent = mockAgent("Agent1", "out");
        when(agentRegistry.requireAgent("Agent1")).thenReturn(agent);

        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "Agent1", Set.of(), Map.of()))
                .build();

        executorService.execute(dag, "input", null, Map.of());

        assertNotNull(meterRegistry.find("dag.execution.duration").timer());
        assertNotNull(meterRegistry.find("dag.node.duration").timer());
    }

    private Agent mockAgent(String name, String output) {
        Agent agent = mock(Agent.class);
        when(agent.getName()).thenReturn(name);
        when(agent.execute(any(AgentRequest.class)))
                .thenReturn(AgentResponse.success(name, output, 10));
        return agent;
    }
}
