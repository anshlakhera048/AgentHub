package com.agenthub.agent.impl;

import com.agenthub.agent.Agent;
import com.agenthub.llm.LLMClient;
import com.agenthub.llm.LLMOptions;
import com.agenthub.memory.InMemoryShortTermMemory;
import com.agenthub.memory.DefaultMemoryService;
import com.agenthub.memory.NoOpLongTermMemory;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;
import com.agenthub.prompt.DefaultPromptEngine;
import com.agenthub.prompt.PromptTemplateLoader;
import com.agenthub.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class PromptOptimizerAgentTest {

    private PromptOptimizerAgent agent;
    private StubLLMClient stubLLM;

    @BeforeEach
    void setUp() {
        stubLLM = new StubLLMClient();

        PromptTemplateLoader loader = new PromptTemplateLoader();
        DefaultPromptEngine promptEngine = new DefaultPromptEngine(loader);
        promptEngine.init();

        DefaultMemoryService memoryService = new DefaultMemoryService(
                new InMemoryShortTermMemory(),
                new NoOpLongTermMemory()
        );
        ToolRegistry toolRegistry = new ToolRegistry(List.of());

        agent = new PromptOptimizerAgent(stubLLM, promptEngine, memoryService, toolRegistry);
    }

    @Test
    void nameIsPromptOptimizer() {
        assertEquals("PromptOptimizer", agent.getName());
    }

    @Test
    void descriptionIsNotEmpty() {
        assertNotNull(agent.getDescription());
        assertFalse(agent.getDescription().isBlank());
    }

    @Test
    void executeReturnsSuccessfulResponse() {
        stubLLM.setResponse("**Optimized Prompt:** Explain Apache Kafka in simple terms.");

        AgentRequest request = new AgentRequest(
                "PromptOptimizer",
                "Explain Kafka simply",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
        assertEquals("PromptOptimizer", response.agentName());
        assertNotNull(response.output());
        assertTrue(response.output().contains("Optimized Prompt"));
        assertTrue(response.latencyMs() >= 0);
    }

    @Test
    void executeWithSessionIdStoresMemory() {
        stubLLM.setResponse("Optimized prompt output");
        UUID sessionId = UUID.randomUUID();

        AgentRequest request = new AgentRequest(
                "PromptOptimizer",
                "Write a haiku",
                sessionId, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
        assertEquals("Optimized prompt output", response.output());
    }

    @Test
    void executeHandlesLLMFailure() {
        stubLLM.setShouldFail(true);

        AgentRequest request = new AgentRequest(
                "PromptOptimizer",
                "test input",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertFalse(response.success());
        assertNotNull(response.errorMessage());
    }

    @Test
    void executeIncludesCustomOptimizationGoal() {
        stubLLM.setCapturePrompt(true);
        stubLLM.setResponse("output");

        AgentRequest request = new AgentRequest(
                "PromptOptimizer",
                "test input",
                null,
                Map.of("goal", "conciseness"),
                null, null
        );

        agent.execute(request);

        String captured = stubLLM.getLastPrompt();
        assertNotNull(captured);
        assertTrue(captured.contains("conciseness"));
    }

    // --- Stub LLM Client for testing ---

    private static class StubLLMClient implements LLMClient {
        private String response = "stub response";
        private boolean shouldFail = false;
        private boolean capturePrompt = false;
        private String lastPrompt;

        void setResponse(String r) { this.response = r; }
        void setShouldFail(boolean f) { this.shouldFail = f; }
        void setCapturePrompt(boolean c) { this.capturePrompt = c; }
        String getLastPrompt() { return lastPrompt; }

        @Override
        public String generate(String prompt) {
            return generate(prompt, LLMOptions.defaults());
        }

        @Override
        public String generate(String prompt, LLMOptions options) {
            if (capturePrompt) lastPrompt = prompt;
            if (shouldFail) throw new RuntimeException("LLM failure");
            return response;
        }

        @Override
        public CompletableFuture<String> generateAsync(String prompt) {
            return CompletableFuture.completedFuture(generate(prompt));
        }

        @Override
        public CompletableFuture<String> generateAsync(String prompt, LLMOptions options) {
            return CompletableFuture.completedFuture(generate(prompt, options));
        }
    }
}
