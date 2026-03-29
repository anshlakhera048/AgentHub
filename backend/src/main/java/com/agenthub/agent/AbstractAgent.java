package com.agenthub.agent;

import com.agenthub.llm.LLMClient;
import com.agenthub.llm.LLMOptions;
import com.agenthub.memory.MemoryService;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;
import com.agenthub.prompt.PromptEngine;
import com.agenthub.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Base class for agents providing common LLM, memory, prompt, and tool access.
 */
public abstract class AbstractAgent implements Agent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final LLMClient llmClient;
    protected final PromptEngine promptEngine;
    protected final MemoryService memoryService;
    protected final ToolRegistry toolRegistry;

    protected AbstractAgent(LLMClient llmClient, PromptEngine promptEngine,
                            MemoryService memoryService, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.promptEngine = promptEngine;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public AgentResponse execute(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Agent '{}' executing request, input length={}", getName(), request.input().length());

            // 1. Retrieve memory context
            String memoryContext = retrieveMemoryContext(request);

            // 2. Build the prompt
            String prompt = buildPrompt(request, memoryContext);

            // 3. Call LLM
            String output = callLLM(prompt, request);

            // 4. Store interaction in memory
            storeInMemory(request, output);

            long latency = System.currentTimeMillis() - startTime;
            log.info("Agent '{}' completed in {}ms", getName(), latency);

            return AgentResponse.success(getName(), output, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Agent '{}' failed after {}ms: {}", getName(), latency, e.getMessage(), e);
            return AgentResponse.failure(getName(), e.getMessage());
        }
    }

    protected String retrieveMemoryContext(AgentRequest request) {
        if (request.sessionId() == null) return "";
        try {
            String shortTermMemory = memoryService.getShortTermMemory(request.sessionId().toString());
            String longTermMemory = memoryService.retrieveRelevantContext(request.input(), 3);
            StringBuilder sb = new StringBuilder();
            if (shortTermMemory != null && !shortTermMemory.isBlank()) {
                sb.append("Recent conversation:\n").append(shortTermMemory).append("\n\n");
            }
            if (longTermMemory != null && !longTermMemory.isBlank()) {
                sb.append("Relevant knowledge:\n").append(longTermMemory).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to retrieve memory context: {}", e.getMessage());
            return "";
        }
    }

    protected abstract String buildPrompt(AgentRequest request, String memoryContext);

    protected String callLLM(String prompt, AgentRequest request) {
        LLMOptions options = LLMOptions.defaults();
        if (request.parameters().containsKey("model")) {
            options = LLMOptions.builder()
                    .model((String) request.parameters().get("model"))
                    .build();
        }
        return llmClient.generate(prompt, options);
    }

    protected void storeInMemory(AgentRequest request, String output) {
        if (request.sessionId() == null) return;
        try {
            String sessionId = request.sessionId().toString();
            String interaction = "User: " + request.input() + "\nAssistant (" + getName() + "): " + output;
            memoryService.storeShortTermMemory(sessionId, interaction);
        } catch (Exception e) {
            log.warn("Failed to store memory: {}", e.getMessage());
        }
    }

    protected String getPromptTemplate() {
        return promptEngine.getTemplate(getName());
    }

    protected String renderPrompt(String template, Map<String, String> variables) {
        return promptEngine.render(template, variables);
    }
}
