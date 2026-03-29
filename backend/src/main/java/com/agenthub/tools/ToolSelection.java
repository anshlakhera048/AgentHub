package com.agenthub.tools;

import java.util.Map;

/**
 * Result of LLM-driven tool selection. Contains the tool name and parameters
 * extracted from the LLM's structured response, or a flag indicating no tool is needed.
 */
public record ToolSelection(
        boolean toolRequired,
        String toolName,
        Map<String, Object> parameters,
        String reasoning
) {
    public static ToolSelection none(String reasoning) {
        return new ToolSelection(false, null, Map.of(), reasoning);
    }

    public static ToolSelection of(String toolName, Map<String, Object> parameters, String reasoning) {
        return new ToolSelection(true, toolName, parameters != null ? parameters : Map.of(), reasoning);
    }
}
