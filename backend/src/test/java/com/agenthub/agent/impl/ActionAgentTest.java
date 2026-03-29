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
import com.agenthub.tools.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ActionAgentTest {

    private ActionAgent agent;
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

        Tool echoTool = new Tool() {
            @Override public String getName() { return "Echo"; }
            @Override public String getDescription() { return "Echoes input text"; }
            @Override public Object execute(Map<String, Object> params) {
                return "Echo: " + params.getOrDefault("text", "");
            }
        };

        ToolRegistry toolRegistry = new ToolRegistry(List.of(echoTool));
        ToolSelectionService selectionService = new ToolSelectionService(
                stubLLM, toolRegistry, new ObjectMapper());
        ToolExecutionPipeline toolPipeline = new ToolExecutionPipeline(selectionService, toolRegistry);

        agent = new ActionAgent(stubLLM, promptEngine, memoryService, toolRegistry, toolPipeline);
    }

    @Test
    void nameIsActionAgent() {
        assertEquals("ActionAgent", agent.getName());
    }

    @Test
    void descriptionIsNotEmpty() {
        assertNotNull(agent.getDescription());
        assertFalse(agent.getDescription().isBlank());
    }

    @Test
    void executeWithToolSelection() {
        // First LLM call: tool selection → return Echo tool
        // Second LLM call: final response using tool output
        stubLLM.setResponses(List.of(
                """
                {"tool": "Echo", "params": {"text": "hello world"}, "reasoning": "user wants echo"}
                """,
                "The echo tool returned: Echo: hello world"
        ));

        AgentRequest request = new AgentRequest(
                "ActionAgent",
                "Echo hello world",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
        assertEquals("ActionAgent", response.agentName());
        assertNotNull(response.output());
    }

    @Test
    void executeWithNoToolNeeded() {
        stubLLM.setResponses(List.of(
                """
                {"tool": "none", "params": {}, "reasoning": "no tool needed"}
                """,
                "The answer to 2+2 is 4."
        ));

        AgentRequest request = new AgentRequest(
                "ActionAgent",
                "What is 2+2?",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
    }

    @Test
    void executeHandlesLLMFailure() {
        stubLLM.setFailAfterCalls(0); // fail on first call (tool selection)

        AgentRequest request = new AgentRequest(
                "ActionAgent",
                "test input",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        // The agent should still work — it wraps exceptions via AbstractAgent
        assertNotNull(response);
    }

    @Test
    void executeIncludesToolOutputInPrompt() {
        stubLLM.setCapturePrompt(true);
        stubLLM.setResponses(List.of(
                """
                {"tool": "Echo", "params": {"text": "captured"}, "reasoning": "test"}
                """,
                "Final response"
        ));

        AgentRequest request = new AgentRequest(
                "ActionAgent",
                "Echo captured",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
        // The second LLM call (final response) should contain the tool output
        String lastPrompt = stubLLM.getLastPrompt();
        assertNotNull(lastPrompt);
        assertTrue(lastPrompt.contains("Echo: captured"),
                "Final prompt should contain tool output");
    }

    // --- Stub LLM Client ---

    private static class StubLLMClient implements LLMClient {
        private List<String> responses;
        private int callCount = 0;
        private int failAfterCalls = -1;
        private boolean capturePrompt = false;
        private String lastPrompt;

        void setResponses(List<String> r) { this.responses = r; this.callCount = 0; }
        void setFailAfterCalls(int n) { this.failAfterCalls = n; }
        void setCapturePrompt(boolean c) { this.capturePrompt = c; }
        String getLastPrompt() { return lastPrompt; }

        @Override
        public String generate(String prompt) { return generate(prompt, LLMOptions.defaults()); }

        @Override
        public String generate(String prompt, LLMOptions options) {
            if (capturePrompt) lastPrompt = prompt;
            if (failAfterCalls >= 0 && callCount >= failAfterCalls) {
                throw new RuntimeException("LLM failure");
            }
            if (responses != null && callCount < responses.size()) {
                return responses.get(callCount++);
            }
            callCount++;
            return "stub response";
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
