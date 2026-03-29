package com.agenthub.tools;

import com.agenthub.llm.LLMClient;
import com.agenthub.llm.LLMOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Uses the LLM to select the appropriate tool and extract parameters from user input.
 */
@Service
public class ToolSelectionService {

    private static final Logger log = LoggerFactory.getLogger(ToolSelectionService.class);

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public ToolSelectionService(LLMClient llmClient, ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    public ToolSelection selectTool(String userInput) {
        Collection<Tool> tools = toolRegistry.getAllTools();

        if (tools.isEmpty()) {
            log.info("No tools available, skipping tool selection");
            return ToolSelection.none("No tools available");
        }

        String toolList = tools.stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription())
                .collect(Collectors.joining("\n"));

        String prompt = buildSelectionPrompt(toolList, userInput);

        try {
            LLMOptions options = LLMOptions.builder()
                    .temperature(0.1)
                    .maxTokens(512)
                    .build();

            String response = llmClient.generate(prompt, options);
            return parseSelection(response);
        } catch (Exception e) {
            log.warn("Tool selection via LLM failed: {}", e.getMessage());
            return ToolSelection.none("LLM tool selection failed: " + e.getMessage());
        }
    }

    ToolSelection parseSelection(String llmResponse) {
        try {
            // Extract JSON from the response — handle markdown code fences
            String json = extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(json);

            String toolName = root.path("tool").asText(null);
            String reasoning = root.path("reasoning").asText("");

            if (toolName == null || toolName.isBlank() || "none".equalsIgnoreCase(toolName)) {
                return ToolSelection.none(reasoning);
            }

            Map<String, Object> params = Map.of();
            JsonNode paramsNode = root.path("params");
            if (paramsNode.isObject()) {
                params = objectMapper.convertValue(paramsNode,
                        new TypeReference<Map<String, Object>>() {});
            }

            // Validate the tool exists
            if (toolRegistry.getTool(toolName).isEmpty()) {
                log.warn("LLM selected non-existent tool '{}', falling back to none", toolName);
                return ToolSelection.none("Selected tool '" + toolName + "' does not exist");
            }

            log.info("LLM selected tool='{}' with params={}, reasoning='{}'",
                    toolName, params.keySet(), reasoning);
            return ToolSelection.of(toolName, params, reasoning);
        } catch (Exception e) {
            log.warn("Failed to parse tool selection response: {}", e.getMessage());
            return ToolSelection.none("Failed to parse LLM response");
        }
    }

    private String buildSelectionPrompt(String toolList, String userInput) {
        return """
                You are an AI tool selector. Given the user request, decide which tool (if any) should be used.
                
                Available tools:
                %s
                
                User request:
                %s
                
                Instructions:
                - If a tool is needed, return a JSON object with "tool", "params", and "reasoning"
                - If no tool is needed, return {"tool": "none", "params": {}, "reasoning": "explain why"}
                - The params must match what the tool expects
                - For FileReader, use param "path" (string)
                - For HttpClient, use param "url" (string), optionally "method" (GET/POST)
                - For CodeExecution, use param "code" (string), optionally "language" (python)
                
                Return ONLY valid JSON, no other text:
                """.formatted(toolList, userInput);
    }

    private static String extractJson(String text) {
        if (text == null) return "{}";
        String trimmed = text.strip();
        // Strip markdown code fences
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        // Find first { and last }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }
}
