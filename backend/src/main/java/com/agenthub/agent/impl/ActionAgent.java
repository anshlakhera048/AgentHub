package com.agenthub.agent.impl;

import com.agenthub.agent.AbstractAgent;
import com.agenthub.llm.LLMClient;
import com.agenthub.memory.MemoryService;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.prompt.PromptEngine;
import com.agenthub.tools.ToolExecutionPipeline;
import com.agenthub.tools.ToolExecutionResult;
import com.agenthub.tools.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool-enabled agent that uses LLM-driven tool selection to take actions.
 * Flow:
 * 1. Select + execute tool via ToolExecutionPipeline
 * 2. Combine tool output with memory context
 * 3. Send combined context to LLM for final response
 */
@Component
public class ActionAgent extends AbstractAgent {

    private final ToolExecutionPipeline toolPipeline;

    public ActionAgent(LLMClient llmClient, PromptEngine promptEngine,
                       MemoryService memoryService, ToolRegistry toolRegistry,
                       ToolExecutionPipeline toolPipeline) {
        super(llmClient, promptEngine, memoryService, toolRegistry);
        this.toolPipeline = toolPipeline;
    }

    @Override
    public String getName() {
        return "ActionAgent";
    }

    @Override
    public String getDescription() {
        return "Tool-enabled agent that can read files, make HTTP requests, and execute code " +
               "to fulfill user requests. Uses LLM-driven tool selection.";
    }

    @Override
    protected String buildPrompt(AgentRequest request, String memoryContext) {
        // 1. Run tool pipeline
        ToolExecutionResult toolResult = toolPipeline.execute(request.input());

        String toolOutput = "";
        String toolInfo = "";

        if (toolResult.toolUsed()) {
            if (toolResult.success()) {
                toolOutput = toolResult.output();
                toolInfo = "Tool '" + toolResult.toolName() + "' executed successfully.";
                log.info("ActionAgent used tool '{}', output length={}", toolResult.toolName(),
                        toolOutput.length());
            } else {
                toolInfo = "Tool '" + toolResult.toolName() + "' failed: " + toolResult.errorMessage();
                log.warn("ActionAgent tool '{}' failed: {}", toolResult.toolName(), toolResult.errorMessage());
            }
        } else {
            toolInfo = "No tool was needed for this request.";
        }

        // 2. Build the prompt with tool output + memory context
        String template = getPromptTemplate();
        Map<String, String> vars = new HashMap<>();
        vars.put("user_input", request.input());
        vars.put("memory_context", memoryContext);
        vars.put("tool_output", toolOutput);
        vars.put("tool_info", toolInfo);
        return renderPrompt(template, vars);
    }
}
