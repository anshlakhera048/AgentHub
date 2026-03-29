package com.agenthub.agent.impl;

import com.agenthub.agent.AbstractAgent;
import com.agenthub.llm.LLMClient;
import com.agenthub.memory.MemoryService;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.prompt.PromptEngine;
import com.agenthub.tools.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PromptOptimizerAgent extends AbstractAgent {

    public PromptOptimizerAgent(LLMClient llmClient, PromptEngine promptEngine,
                                 MemoryService memoryService, ToolRegistry toolRegistry) {
        super(llmClient, promptEngine, memoryService, toolRegistry);
    }

    @Override
    public String getName() {
        return "PromptOptimizer";
    }

    @Override
    public String getDescription() {
        return "Analyzes and optimizes user prompts for better LLM responses. " +
               "Improves clarity, adds constraints, and structures the prompt effectively.";
    }

    @Override
    protected String buildPrompt(AgentRequest request, String memoryContext) {
        String template = getPromptTemplate();
        Map<String, String> vars = new HashMap<>();
        vars.put("user_input", request.input());
        vars.put("memory_context", memoryContext);
        vars.put("optimization_goal", request.parameters()
                .getOrDefault("goal", "clarity and effectiveness").toString());
        return renderPrompt(template, vars);
    }
}
