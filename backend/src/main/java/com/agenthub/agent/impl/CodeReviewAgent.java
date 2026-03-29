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
public class CodeReviewAgent extends AbstractAgent {

    public CodeReviewAgent(LLMClient llmClient, PromptEngine promptEngine,
                           MemoryService memoryService, ToolRegistry toolRegistry) {
        super(llmClient, promptEngine, memoryService, toolRegistry);
    }

    @Override
    public String getName() {
        return "CodeReview";
    }

    @Override
    public String getDescription() {
        return "Reviews code for bugs, security issues, performance problems, " +
               "and best practice violations. Provides actionable improvement suggestions.";
    }

    @Override
    protected String buildPrompt(AgentRequest request, String memoryContext) {
        String template = getPromptTemplate();
        Map<String, String> vars = new HashMap<>();
        vars.put("user_input", request.input());
        vars.put("memory_context", memoryContext);
        vars.put("language", request.parameters()
                .getOrDefault("language", "auto-detect").toString());
        vars.put("review_focus", request.parameters()
                .getOrDefault("focus", "bugs,security,performance,best-practices").toString());
        return renderPrompt(template, vars);
    }
}
