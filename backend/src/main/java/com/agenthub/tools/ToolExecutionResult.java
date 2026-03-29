package com.agenthub.tools;

import java.util.Map;

/**
 * Result of a tool execution, including the selected tool, output, and timing.
 */
public record ToolExecutionResult(
        boolean toolUsed,
        boolean success,
        String toolName,
        Map<String, Object> parameters,
        String output,
        String errorMessage,
        long latencyMs,
        String reasoning
) {
    public static ToolExecutionResult noToolUsed(String reasoning) {
        return new ToolExecutionResult(false, true, null, Map.of(), "", null, 0, reasoning);
    }

    public static ToolExecutionResult success(String toolName, Map<String, Object> params,
                                               String output, long latency, String reasoning) {
        return new ToolExecutionResult(true, true, toolName, params, output, null, latency, reasoning);
    }

    public static ToolExecutionResult failure(String toolName, Map<String, Object> params,
                                               String errorMessage, long latency, String reasoning) {
        return new ToolExecutionResult(true, false, toolName, params, "",
                errorMessage, latency, reasoning);
    }
}
