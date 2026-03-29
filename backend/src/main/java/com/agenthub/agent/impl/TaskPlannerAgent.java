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
public class TaskPlannerAgent extends AbstractAgent {

    public TaskPlannerAgent(LLMClient llmClient, PromptEngine promptEngine,
                            MemoryService memoryService, ToolRegistry toolRegistry) {
        super(llmClient, promptEngine, memoryService, toolRegistry);
    }

    @Override
    public String getName() {
        return "TaskPlanner";
    }

    @Override
    public String getDescription() {
        return "Breaks down complex tasks into structured, actionable sub-tasks. " +
               "Creates execution plans with dependencies and priorities.";
    }

    @Override
    protected String buildPrompt(AgentRequest request, String memoryContext) {
        String template = getPromptTemplate();
        Map<String, String> vars = new HashMap<>();
        vars.put("user_input", request.input());
        vars.put("memory_context", memoryContext);
        vars.put("complexity_level", request.parameters()
                .getOrDefault("complexity", "medium").toString());
        return renderPrompt(template, vars);
    }
}
