package com.agenthub.agent.impl;

import com.agenthub.llm.LLMClient;
import com.agenthub.llm.LLMOptions;
import com.agenthub.memory.DefaultMemoryService;
import com.agenthub.memory.InMemoryShortTermMemory;
import com.agenthub.memory.NoOpLongTermMemory;
import com.agenthub.memory.embedding.EmbeddingService;
import com.agenthub.memory.vector.InMemoryVectorStore;
import com.agenthub.memory.vector.SemanticSearchService;
import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;
import com.agenthub.prompt.DefaultPromptEngine;
import com.agenthub.prompt.PromptTemplateLoader;
import com.agenthub.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeRetrievalAgentTest {

    private KnowledgeRetrievalAgent agent;
    private StubLLMClient stubLLM;
    private InMemoryVectorStore vectorStore;
    private StubEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        stubLLM = new StubLLMClient();

        PromptTemplateLoader loader = new PromptTemplateLoader();
        DefaultPromptEngine promptEngine = new DefaultPromptEngine(loader);
        promptEngine.init();

        DefaultMemoryService memoryService = new DefaultMemoryService(
                new InMemoryShortTermMemory(),
                new NoOpLongTermMemory()
        );
        ToolRegistry toolRegistry = new ToolRegistry(List.of());

        vectorStore = new InMemoryVectorStore();
        embeddingService = new StubEmbeddingService();
        SemanticSearchService searchService = new SemanticSearchService(vectorStore, embeddingService, 5);

        agent = new KnowledgeRetrievalAgent(stubLLM, promptEngine, memoryService, toolRegistry, searchService);
    }

    @Test
    void nameIsKnowledgeRetrieval() {
        assertEquals("KnowledgeRetrieval", agent.getName());
    }

    @Test
    void descriptionIsNotEmpty() {
        assertNotNull(agent.getDescription());
        assertFalse(agent.getDescription().isBlank());
    }

    @Test
    void executeReturnsSuccessfulResponse() {
        stubLLM.setResponse("**Answer:** Based on the context, here is the answer.");

        AgentRequest request = new AgentRequest(
                "KnowledgeRetrieval",
                "What is machine learning?",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
        assertEquals("KnowledgeRetrieval", response.agentName());
        assertNotNull(response.output());
    }

    @Test
    void executeWithVectorStoreContextInjectsKnowledge() {
        // Seed the vector store with knowledge
        float[] embedding = {1.0f, 0.0f, 0.0f};
        vectorStore.store("ml_chunk_0", "Machine learning is a subset of AI.", embedding);
        embeddingService.setEmbedding(new float[]{0.9f, 0.1f, 0.0f}); // close to stored

        stubLLM.setCapturePrompt(true);
        stubLLM.setResponse("ML answer");

        AgentRequest request = new AgentRequest(
                "KnowledgeRetrieval",
                "What is ML?",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);

        assertTrue(response.success());
        String capturedPrompt = stubLLM.getLastPrompt();
        assertNotNull(capturedPrompt);
        assertTrue(capturedPrompt.contains("Machine learning is a subset of AI"),
                "Prompt should include retrieved knowledge");
    }

    @Test
    void executeWithNoKnowledgeStillSucceeds() {
        // Empty vector store
        stubLLM.setResponse("I don't have enough context.");

        AgentRequest request = new AgentRequest(
                "KnowledgeRetrieval",
                "Obscure question",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);
        assertTrue(response.success());
    }

    @Test
    void executeHandlesLLMFailure() {
        stubLLM.setShouldFail(true);

        AgentRequest request = new AgentRequest(
                "KnowledgeRetrieval",
                "test input",
                null, Map.of(), null, null
        );

        AgentResponse response = agent.execute(request);
        assertFalse(response.success());
        assertNotNull(response.errorMessage());
    }

    @Test
    void executeWithNoSearchServiceStillWorks() {
        // Agent with no-op search service
        SemanticSearchService noOpSearch = new SemanticSearchService(null, null, 5);
        KnowledgeRetrievalAgent agentNoVector = new KnowledgeRetrievalAgent(
                stubLLM,
                new DefaultPromptEngine(new PromptTemplateLoader()) {{ init(); }},
                new DefaultMemoryService(new InMemoryShortTermMemory(), new NoOpLongTermMemory()),
                new ToolRegistry(List.of()),
                noOpSearch
        );

        stubLLM.setResponse("fallback answer");

        AgentRequest request = new AgentRequest(
                "KnowledgeRetrieval",
                "test query",
                null, Map.of(), null, null
        );

        AgentResponse response = agentNoVector.execute(request);
        assertTrue(response.success());
    }

    // --- Stubs ---

    private static class StubEmbeddingService implements EmbeddingService {
        private float[] embedding = {0.5f, 0.5f, 0.5f};

        void setEmbedding(float[] e) { this.embedding = e; }

        @Override
        public float[] embed(String text) {
            return embedding;
        }
    }

    private static class StubLLMClient implements LLMClient {
        private String response = "stub response";
        private boolean shouldFail = false;
        private boolean capturePrompt = false;
        private String lastPrompt;

        void setResponse(String r) { this.response = r; }
        void setShouldFail(boolean f) { this.shouldFail = f; }
        void setCapturePrompt(boolean c) { this.capturePrompt = c; }
        String getLastPrompt() { return lastPrompt; }

        @Override
        public String generate(String prompt) {
            return generate(prompt, LLMOptions.defaults());
        }

        @Override
        public String generate(String prompt, LLMOptions options) {
            if (capturePrompt) lastPrompt = prompt;
            if (shouldFail) throw new RuntimeException("LLM failure");
            return response;
        }

        @Override
        public CompletableFuture<String> generateAsync(String prompt) {
            return CompletableFuture.completedFuture(generate(prompt));
        }

        @Override
        public CompletableFuture<String> generateAsync(String prompt, LLMOptions options) {
            return CompletableFuture.completedFuture(generate(prompt, options));
        }
    }
}
