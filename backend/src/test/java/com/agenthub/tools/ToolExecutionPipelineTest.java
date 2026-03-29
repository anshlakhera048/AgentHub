package com.agenthub.tools;

import com.agenthub.llm.LLMClient;
import com.agenthub.llm.LLMOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionPipelineTest {

    private ToolExecutionPipeline pipeline;
    private StubLLMClient stubLLM;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        stubLLM = new StubLLMClient();

        Tool echoTool = new Tool() {
            @Override public String getName() { return "Echo"; }
            @Override public String getDescription() { return "Echoes input"; }
            @Override public Object execute(Map<String, Object> params) {
                return "Echo: " + params.get("text");
            }
        };

        Tool failTool = new Tool() {
            @Override public String getName() { return "FailTool"; }
            @Override public String getDescription() { return "Always fails"; }
            @Override public Object execute(Map<String, Object> params) {
                throw new RuntimeException("Intentional failure");
            }
        };

        toolRegistry = new ToolRegistry(List.of(echoTool, failTool));
        ToolSelectionService selectionService = new ToolSelectionService(
                stubLLM, toolRegistry, new ObjectMapper());
        pipeline = new ToolExecutionPipeline(selectionService, toolRegistry);
    }

    @Test
    void executeReturnsNoToolWhenLLMSelectsNone() {
        stubLLM.setResponse("""
                {"tool": "none", "params": {}, "reasoning": "no tool needed"}
                """);

        ToolExecutionResult result = pipeline.execute("What is 2+2?");

        assertFalse(result.toolUsed());
        assertTrue(result.success());
    }

    @Test
    void executeRunsSelectedTool() {
        stubLLM.setResponse("""
                {"tool": "Echo", "params": {"text": "hello"}, "reasoning": "echo test"}
                """);

        ToolExecutionResult result = pipeline.execute("Echo hello");

        assertTrue(result.toolUsed());
        assertTrue(result.success());
        assertEquals("Echo", result.toolName());
        assertEquals("Echo: hello", result.output());
        assertTrue(result.latencyMs() >= 0);
    }

    @Test
    void executeHandlesToolFailure() {
        stubLLM.setResponse("""
                {"tool": "FailTool", "params": {}, "reasoning": "test failure"}
                """);

        ToolExecutionResult result = pipeline.execute("break something");

        assertTrue(result.toolUsed());
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void executeDirectRunsTool() {
        ToolExecutionResult result = pipeline.executeDirect("Echo", Map.of("text", "direct"));

        assertTrue(result.toolUsed());
        assertTrue(result.success());
        assertEquals("Echo: direct", result.output());
    }

    @Test
    void executeDirectHandlesFailure() {
        ToolExecutionResult result = pipeline.executeDirect("FailTool", Map.of());

        assertTrue(result.toolUsed());
        assertFalse(result.success());
    }

    // --- Stub LLM Client ---

    private static class StubLLMClient implements LLMClient {
        private String response = "{}";

        void setResponse(String r) { this.response = r; }

        @Override
        public String generate(String prompt) { return generate(prompt, LLMOptions.defaults()); }

        @Override
        public String generate(String prompt, LLMOptions options) { return response; }

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
