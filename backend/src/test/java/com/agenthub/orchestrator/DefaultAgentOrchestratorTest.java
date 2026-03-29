package com.agenthub.orchestrator;

import com.agenthub.agent.Agent;
import com.agenthub.agent.AgentNotFoundException;
import com.agenthub.agent.AgentRegistry;
import com.agenthub.llm.LLMClient;
import com.agenthub.memory.MemoryService;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;
import com.agenthub.orchestrator.dag.DAGExecutorService;
import com.agenthub.prompt.PromptEngine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DefaultAgentOrchestratorTest {

    private DefaultAgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        Agent stubAgent = new Agent() {
            @Override
            public String getName() { return "StubAgent"; }

            @Override
            public String getDescription() { return "Test stub"; }

            @Override
            public AgentResponse execute(AgentRequest request) {
                return AgentResponse.success("StubAgent", "Processed: " + request.input(), 10);
            }
        };

        AgentRegistry registry = new AgentRegistry(List.of(stubAgent));
        orchestrator = new DefaultAgentOrchestrator(
                registry, new SimpleMeterRegistry(),
                mock(DAGExecutorService.class),
                mock(LLMClient.class), mock(PromptEngine.class), mock(MemoryService.class));
    }

    @Test
    void executeSingleAgentReturnsSuccess() {
        AgentRequest request = new AgentRequest(
                "StubAgent", "hello", null, Map.of(), null, null
        );

        AgentResponse response = orchestrator.execute(request);

        assertTrue(response.success());
        assertEquals("StubAgent", response.agentName());
        assertEquals("Processed: hello", response.output());
    }

    @Test
    void executeThrowsForUnknownAgent() {
        AgentRequest request = new AgentRequest(
                "NonExistent", "hello", null, Map.of(), null, null
        );

        assertThrows(AgentNotFoundException.class, () -> orchestrator.execute(request));
    }

    @Test
    void executeChainPassesOutputAsInput() {
        Agent upperAgent = new Agent() {
            @Override
            public String getName() { return "Upper"; }

            @Override
            public String getDescription() { return "Uppercases"; }

            @Override
            public AgentResponse execute(AgentRequest request) {
                return AgentResponse.success("Upper", request.input().toUpperCase(), 5);
            }
        };

        Agent suffixAgent = new Agent() {
            @Override
            public String getName() { return "Suffix"; }

            @Override
            public String getDescription() { return "Adds suffix"; }

            @Override
            public AgentResponse execute(AgentRequest request) {
                return AgentResponse.success("Suffix", request.input() + "!!!", 5);
            }
        };

        AgentRegistry registry = new AgentRegistry(List.of(upperAgent, suffixAgent));
        DefaultAgentOrchestrator chainOrchestrator =
                new DefaultAgentOrchestrator(registry, new SimpleMeterRegistry(),
                        mock(DAGExecutorService.class),
                        mock(LLMClient.class), mock(PromptEngine.class), mock(MemoryService.class));

        AgentRequest request = new AgentRequest(
                "Upper", "hello", null, Map.of(), List.of("Suffix"), null
        );

        AgentResponse response = chainOrchestrator.execute(request);

        assertTrue(response.success());
        assertEquals("HELLO!!!", response.output());
        assertEquals(2, response.chainResults().size());
    }
}
