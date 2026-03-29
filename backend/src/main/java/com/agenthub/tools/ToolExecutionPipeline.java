package com.agenthub.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Orchestrates the tool execution pipeline:
 * 1. Select tool via LLM
 * 2. Execute the selected tool
 * 3. Return the combined result
 */
@Service
public class ToolExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionPipeline.class);

    private final ToolSelectionService selectionService;
    private final ToolRegistry toolRegistry;

    public ToolExecutionPipeline(ToolSelectionService selectionService, ToolRegistry toolRegistry) {
        this.selectionService = selectionService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Run the full pipeline: select a tool for the input, execute it, and return the result.
     */
    public ToolExecutionResult execute(String userInput) {
        long startTime = System.currentTimeMillis();

        // 1. Select tool
        ToolSelection selection = selectionService.selectTool(userInput);

        if (!selection.toolRequired()) {
            log.info("No tool required for input: {}", truncate(userInput, 80));
            return ToolExecutionResult.noToolUsed(selection.reasoning());
        }

        // 2. Execute tool
        try {
            log.info("Executing tool '{}' with params: {}", selection.toolName(), selection.parameters().keySet());
            Object result = toolRegistry.executeTool(selection.toolName(), selection.parameters());
            long latency = System.currentTimeMillis() - startTime;

            String resultStr = result != null ? result.toString() : "";
            log.info("Tool '{}' completed in {}ms, result length={}", selection.toolName(), latency, resultStr.length());

            return ToolExecutionResult.success(selection.toolName(), selection.parameters(),
                    resultStr, latency, selection.reasoning());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Tool '{}' execution failed after {}ms: {}", selection.toolName(), latency, e.getMessage());
            return ToolExecutionResult.failure(selection.toolName(), selection.parameters(),
                    e.getMessage(), latency, selection.reasoning());
        }
    }

    /**
     * Execute a specific tool directly (no LLM selection).
     */
    public ToolExecutionResult executeDirect(String toolName, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        try {
            Object result = toolRegistry.executeTool(toolName, params);
            long latency = System.currentTimeMillis() - startTime;
            String resultStr = result != null ? result.toString() : "";
            return ToolExecutionResult.success(toolName, params, resultStr, latency, "Direct execution");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            return ToolExecutionResult.failure(toolName, params, e.getMessage(), latency, "Direct execution");
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
