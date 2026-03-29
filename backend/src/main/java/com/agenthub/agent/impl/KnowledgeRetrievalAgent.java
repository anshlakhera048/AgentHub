package com.agenthub.agent.impl;

import com.agenthub.agent.AbstractAgent;
import com.agenthub.llm.LLMClient;
import com.agenthub.memory.MemoryService;
import com.agenthub.memory.vector.SemanticSearchService;
import com.agenthub.memory.vector.VectorSearchResult;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.prompt.PromptEngine;
import com.agenthub.tools.ToolRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KnowledgeRetrievalAgent extends AbstractAgent {

    private final SemanticSearchService searchService;

    public KnowledgeRetrievalAgent(LLMClient llmClient, PromptEngine promptEngine,
                                    MemoryService memoryService, ToolRegistry toolRegistry,
                                    SemanticSearchService searchService) {
        super(llmClient, promptEngine, memoryService, toolRegistry);
        this.searchService = searchService;
    }

    @Override
    public String getName() {
        return "KnowledgeRetrieval";
    }

    @Override
    public String getDescription() {
        return "Retrieves and synthesizes information from the knowledge base. " +
               "Uses RAG to find relevant context and generate accurate answers.";
    }

    @Override
    protected String retrieveMemoryContext(AgentRequest request) {
        // Only return short-term conversation history here.
        // Retrieved knowledge is handled separately in buildPrompt().
        if (request.sessionId() == null) return "";
        try {
            String shortTerm = memoryService.getShortTermMemory(request.sessionId().toString());
            if (shortTerm != null && !shortTerm.isBlank()) {
                return "Recent conversation:\n" + shortTerm;
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve memory context: {}", e.getMessage());
        }
        return "";
    }

    @Override
    protected String buildPrompt(AgentRequest request, String memoryContext) {
        String template = getPromptTemplate();

        // Retrieve knowledge via SemanticSearchService
        int topK = Integer.parseInt(
                request.parameters().getOrDefault("topK", "5").toString());
        String retrievedContext = "";
        if (searchService.isAvailable()) {
            List<VectorSearchResult> results = searchService.search(request.input(), topK);
            if (!results.isEmpty()) {
                retrievedContext = results.stream()
                        .map(VectorSearchResult::content)
                        .collect(Collectors.joining("\n\n---\n\n"));
                log.info("Retrieved {} knowledge chunks for query (topK={})", results.size(), topK);
            }
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("user_input", request.input());
        vars.put("memory_context", memoryContext);
        vars.put("retrieved_context", retrievedContext);
        vars.put("answer_style", request.parameters()
                .getOrDefault("style", "detailed and accurate").toString());
        return renderPrompt(template, vars);
    }
}
