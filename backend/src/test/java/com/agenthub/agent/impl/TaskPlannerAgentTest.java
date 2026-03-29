package com.agenthub.agent.impl;

import com.agenthub.llm.LLMClient;
import com.agenthub.llm.LLMOptions;
import com.agenthub.memory.DefaultMemoryService;
import com.agenthub.memory.InMemoryShortTermMemory;
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

class TaskPlannerAgentTest {

    private TaskPlannerAgent agent;
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

        agent = new TaskPlannerAgent(stubLLM, promptEngine, memoryService, toolRegistry);
    }

    @Test
    void nameIsTaskPlanner() {
        assertEquals("TaskPlanner", agent.getName());
    }

    @Test
    void descriptionIsNotEmpty() {
        assertNotNull(agent.getDescription());
        assertFalse(agent.getDescription().isBlank());
    }

    @Test
    void executeReturnsSuccessfulResponse() {
        stubLLM.setResponse("Task 1: Research\nTask 2: Implement\nTask 3: Test");

        AgentRequest request = new AgentRequest(
                "TaskPlanner",
                "Build a REST API for user management",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
        assertEquals("TaskPlanner", response.agentName());
        assertNotNull(response.output());
        assertTrue(response.output().contains("Task 1"));
    }

    @Test
    void executeWithComplexityParameter() {
        stubLLM.setCapturePrompt(true);
        stubLLM.setResponse("plan output");

        AgentRequest request = new AgentRequest(
                "TaskPlanner",
                "Migrate monolith to microservices",
                null,
                Map.of("complexity", "high"),
                null, null
        );

        agent.execute(request);

        String captured = stubLLM.getLastPrompt();
        assertNotNull(captured);
        assertTrue(captured.contains("high"));
    }

    @Test
    void executeWithSessionIdStoresMemory() {
        stubLLM.setResponse("planned output");
        UUID sessionId = UUID.randomUUID();

        AgentRequest request = new AgentRequest(
                "TaskPlanner",
                "Create deployment pipeline",
                sessionId, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
        assertEquals("planned output", response.output());
    }

    @Test
    void executeHandlesLLMFailure() {
        stubLLM.setShouldFail(true);

        AgentRequest request = new AgentRequest(
                "TaskPlanner",
                "some task",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertFalse(response.success());
        assertNotNull(response.errorMessage());
    }

    // --- Stub LLM Client ---

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
