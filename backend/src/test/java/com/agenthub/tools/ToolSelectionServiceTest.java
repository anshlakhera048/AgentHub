package com.agenthub.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agenthub.llm.LLMClient;
import com.agenthub.llm.LLMOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ToolSelectionServiceTest {

    private ToolSelectionService selectionService;
    private StubLLMClient stubLLM;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        stubLLM = new StubLLMClient();

        Tool stubTool = new Tool() {
            @Override public String getName() { return "FileReader"; }
            @Override public String getDescription() { return "Reads files"; }
            @Override public Object execute(Map<String, Object> params) { return "content"; }
        };

        toolRegistry = new ToolRegistry(List.of(stubTool));
        selectionService = new ToolSelectionService(stubLLM, toolRegistry, new ObjectMapper());
    }

    @Test
    void parsesValidToolSelection() {
        ToolSelection result = selectionService.parseSelection(
                """
                {"tool": "FileReader", "params": {"path": "test.txt"}, "reasoning": "User wants to read a file"}
                """);

        assertTrue(result.toolRequired());
        assertEquals("FileReader", result.toolName());
        assertEquals("test.txt", result.parameters().get("path"));
    }

    @Test
    void parsesNoneSelection() {
        ToolSelection result = selectionService.parseSelection(
                """
                {"tool": "none", "params": {}, "reasoning": "No tool needed"}
                """);

        assertFalse(result.toolRequired());
    }

    @Test
    void handlesMarkdownCodeFencedJson() {
        ToolSelection result = selectionService.parseSelection(
                """
                ```json
                {"tool": "FileReader", "params": {"path": "data.csv"}, "reasoning": "need file"}
                ```
                """);

        assertTrue(result.toolRequired());
        assertEquals("FileReader", result.toolName());
    }

    @Test
    void handlesGarbageResponse() {
        ToolSelection result = selectionService.parseSelection("This is not JSON at all.");

        assertFalse(result.toolRequired());
        assertNotNull(result.reasoning());
    }

    @Test
    void rejectsNonExistentTool() {
        ToolSelection result = selectionService.parseSelection(
                """
                {"tool": "DoesNotExist", "params": {}, "reasoning": "trying unknown"}
                """);

        assertFalse(result.toolRequired());
    }

    @Test
    void selectToolCallsLLM() {
        stubLLM.setResponse("""
                {"tool": "FileReader", "params": {"path": "readme.md"}, "reasoning": "read file"}
                """);

        ToolSelection result = selectionService.selectTool("Read the readme file");

        assertTrue(result.toolRequired());
        assertEquals("FileReader", result.toolName());
    }

    @Test
    void selectToolHandlesLLMFailure() {
        stubLLM.setShouldFail(true);

        ToolSelection result = selectionService.selectTool("test");

        assertFalse(result.toolRequired());
    }

    @Test
    void selectToolWithEmptyRegistryReturnsNone() {
        ToolRegistry emptyRegistry = new ToolRegistry(List.of());
        ToolSelectionService svc = new ToolSelectionService(stubLLM, emptyRegistry, new ObjectMapper());

        ToolSelection result = svc.selectTool("anything");

        assertFalse(result.toolRequired());
    }

    // --- Stub LLM Client ---

    private static class StubLLMClient implements LLMClient {
        private String response = "{}";
        private boolean shouldFail = false;

        void setResponse(String r) { this.response = r; }
        void setShouldFail(boolean f) { this.shouldFail = f; }

        @Override
        public String generate(String prompt) { return generate(prompt, LLMOptions.defaults()); }

        @Override
        public String generate(String prompt, LLMOptions options) {
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
