# AgentHub — Deep Dive Documentation

## 1. Project Purpose

### Why This Project Was Built

AgentHub was built to demonstrate a production-grade approach to **multi-agent AI orchestration** — the problem of coordinating multiple specialized AI agents to collaboratively solve complex tasks that a single LLM call cannot handle well.

### Real-World Problem Mapping

In enterprise scenarios, different tasks require different reasoning approaches:
- **Code review** needs a systematic, security-focused lens
- **Task planning** needs decomposition and dependency mapping skills
- **Knowledge retrieval** needs precise semantic search combined with synthesis
- **Prompt optimization** needs meta-reasoning about effective communication with LLMs

Rather than fine-tuning one model for all tasks, AgentHub specializes agents and orchestrates their collaboration — similar to how a team of engineers divides responsibilities.

---

## 2. System Design

### End-to-End Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                                  │
│  React UI (port 3000) → Vite proxy → /api/* → Backend                │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼────────────────────────────────────┐
│                       API GATEWAY LAYER                                │
│  MDCRequestFilter → Controllers (Agent, Session, Stream, Workflow)     │
│  GlobalExceptionHandler ← Validation ← CORS (WebConfig)              │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼────────────────────────────────────┐
│                      ORCHESTRATION LAYER                               │
│  AgentService → DefaultAgentOrchestrator                              │
│  ├── Single Agent Execution                                           │
│  ├── Sequential Chain Execution (max depth: 10)                       │
│  ├── DAG Parallel Execution (DAGExecutorService)                      │
│  └── SSE Streaming Execution                                          │
└──────┬──────────────┬──────────────────┬─────────────────────────────┘
       │              │                  │
┌──────▼──────┐ ┌────▼─────────┐ ┌──────▼──────────────────────────────┐
│ AGENT LAYER │ │ PROMPT LAYER │ │           MEMORY LAYER               │
│ AgentRegistry│ │ PromptEngine │ │ ShortTermMemory (Redis/InMemory)    │
│ 5 Agents    │ │ TemplateLoader│ │ LongTermMemory (Vector/ChromaDB)    │
│ AbstractAgent│ │ .txt files   │ │ MemoryService (unified facade)      │
└──────┬──────┘ └──────────────┘ └──────────────────────────────────────┘
       │
┌──────▼──────────────────────────┐  ┌──────────────────────────────────┐
│         TOOL LAYER              │  │          LLM LAYER                │
│ ToolRegistry → ToolSelection    │  │ OllamaLLMClient (WebClient)      │
│ ToolExecutionPipeline           │  │ ├── Synchronous generate()       │
│ ├── FileReaderTool              │  │ ├── Async generateAsync()        │
│ ├── HttpClientTool              │  │ └── Streaming stream() (Flux)    │
│ └── CodeExecutionTool           │  │ Resilience4j retry + CB          │
└─────────────────────────────────┘  └──────────────────────────────────┘
       │                                        │
┌──────▼────────────────────────────────────────▼──────────────────────┐
│                     INFRASTRUCTURE LAYER                               │
│  PostgreSQL (JPA/Hibernate) │ Redis │ ChromaDB │ Ollama (port 11434)  │
└──────────────────────────────────────────────────────────────────────┘
```

### Data Flow (Step-by-Step)

**Single Agent Execution:**

1. HTTP POST → `AgentController.execute()` with `AgentRequest` (agentName, input, sessionId)
2. `AgentService.executeAgent()` persists user message to PostgreSQL
3. `DefaultAgentOrchestrator.execute()` resolves agent from `AgentRegistry`
4. `AbstractAgent.execute()`:
   - a. Retrieves short-term memory (Redis/in-memory) for conversation history
   - b. Retrieves long-term memory (vector search) for relevant knowledge
   - c. Calls `buildPrompt()` (agent-specific template rendering)
   - d. Sends prompt to `OllamaLLMClient.generate()` via WebClient
   - e. Stores interaction in short-term memory
5. Response wrapped in `AgentResponse` with latency, metadata
6. `AgentService` persists assistant message to PostgreSQL
7. HTTP 200 with JSON response returned to client

**Agent Chain Execution:**

1. Same entry point, but `chainAgents` field is populated
2. Orchestrator builds ordered list: [primaryAgent, ...chainAgents]
3. For each agent in sequence:
   - Execute with current input
   - If failure → abort chain, return error
   - Output of current agent becomes input to next agent
4. Final output + all step results returned

**DAG Workflow Execution:**

1. HTTP POST → `WorkflowController.executeWorkflow()` with node definitions
2. Builds a `DAG` (validates acyclicity via topological sort)
3. `DAGExecutorService.execute()`:
   - Computes topological order
   - Schedules nodes to `ExecutorService` thread pool
   - Nodes wait on their dependencies' `CompletableFuture` results
   - Independent nodes execute in parallel
4. Aggregates all node results into `DAGExecutionResult`

### Key Components and Interactions

- **AgentRegistry**: ConcurrentHashMap of agents with alias resolution (kebab-case, lowercase)
- **ToolSelectionService**: Uses LLM to decide which tool to invoke based on user input
- **PromptTemplateLoader**: Loads `.txt` prompt templates from classpath at startup
- **MDCRequestFilter**: Injects request IDs into every request for distributed tracing

---

## 3. Tech Stack Justification

| Technology | Why Chosen | Alternatives Considered |
|-----------|-----------|------------------------|
| **Spring Boot 3.2** | Mature ecosystem, excellent DI, production-ready actuator/metrics. Spring WebFlux for reactive streaming. | Quarkus (faster startup but less ecosystem), Micronaut |
| **Ollama** | Local LLM inference with zero API costs, no data leaves the machine. Supports multiple models. | OpenAI API (expensive, privacy concerns), vLLM (more complex setup) |
| **PostgreSQL** | Robust ACID transactions for session/message persistence, UUID support, JSON columns if needed. | MongoDB (overkill for this schema), SQLite (no concurrent writes) |
| **Redis** | Sub-millisecond read/write for conversation history. TTL support for automatic cleanup. | Memcached (no list data structure), Hazelcast (too heavy) |
| **ChromaDB** | Simple HTTP API for vector storage, no complex setup. Good for RAG prototyping. | Pinecone (cloud-only), Qdrant (better perf but heavier), pgvector (ties DB concerns) |
| **React + Vite** | Fast development iteration, simple component model for chat UI. Vite for instant HMR. | Next.js (overkill for SPA), Svelte (smaller ecosystem) |
| **Resilience4j** | Spring Boot 3 native integration, annotation-driven retry/circuit breaker. | Spring Retry (less features), Hystrix (deprecated) |
| **Flyway** | SQL-based migrations, version control for schema. | Liquibase (XML-heavy), Hibernate auto-DDL (not production-safe) |
| **Micrometer + Prometheus** | Spring Boot native metrics, Prometheus ecosystem for alerting. | Datadog (paid), custom metrics (not standardized) |

---

## 4. Core Modules Breakdown

### 4.1 Agent Module (`com.agenthub.agent`)

**Responsibility:** Define and execute specialized AI agents.

**Key Classes:**
- `Agent` (interface): Contract for all agents — `getName()`, `getDescription()`, `execute()`
- `AbstractAgent`: Template Method pattern providing common execution flow (memory retrieval → prompt building → LLM call → memory storage)
- `AgentRegistry`: Service locator with alias resolution and concurrent registration

**Design Patterns:**
- **Template Method**: `AbstractAgent.execute()` defines the algorithm skeleton; subclasses override `buildPrompt()`
- **Strategy**: Each agent implementation is a strategy for handling a specific task type
- **Service Locator**: `AgentRegistry` maps names to implementations with auto-discovery via Spring DI

**Implementations:**
| Agent | Specialization |
|-------|---------------|
| `PromptOptimizerAgent` | Rewrites user prompts for clarity and effectiveness |
| `TaskPlannerAgent` | Decomposes complex tasks into structured plans |
| `CodeReviewAgent` | Analyzes code for bugs, security, and best practices |
| `KnowledgeRetrievalAgent` | RAG-based retrieval with semantic search |
| `ActionAgent` | Tool-enabled agent using `ToolExecutionPipeline` |

### 4.2 Orchestrator Module (`com.agenthub.orchestrator`)

**Responsibility:** Route requests to agents, manage chains, execute DAG workflows, handle streaming.

**Key Classes:**
- `AgentOrchestrator` (interface): Contract for execution modes (single, chain, async, DAG, stream)
- `DefaultAgentOrchestrator`: Implements all execution modes with metrics instrumentation
- `DAGExecutorService`: Parallel DAG execution with topological ordering
- `DAG` / `DAGNode`: Graph data structures with cycle detection

**Design Patterns:**
- **Facade**: `AgentOrchestrator` hides complexity of routing, chaining, and parallel execution
- **Builder**: `DAG.builder()` for fluent DAG construction
- **Future/Promise**: `CompletableFuture` for async execution and DAG node coordination

### 4.3 LLM Module (`com.agenthub.llm`)

**Responsibility:** Abstract LLM communication (synchronous, async, streaming).

**Key Classes:**
- `LLMClient` (interface): Pluggable LLM abstraction with sync/async/stream methods
- `OllamaLLMClient`: WebClient-based implementation targeting Ollama's REST API
- `LLMOptions`: Immutable configuration record (model, temperature, maxTokens, topP, timeout)
- `LLMException`: Domain exception for LLM failures

**Design Patterns:**
- **Adapter**: `OllamaLLMClient` adapts Ollama's HTTP API to the `LLMClient` interface
- **Builder**: `LLMOptions.builder()` for flexible option construction
- **Decorator** (via Resilience4j annotations): `@Retry` wraps the generate method

### 4.4 Memory Module (`com.agenthub.memory`)

**Responsibility:** Dual memory system — short-term conversation context and long-term knowledge retrieval.

**Key Classes:**
- `MemoryService` (interface): Unified facade for both memory types
- `DefaultMemoryService`: Delegates to `ShortTermMemory` + `LongTermMemory`
- `RedisShortTermMemory`: Redis list-based conversation history with TTL
- `InMemoryShortTermMemory`: `ConcurrentHashMap` fallback for dev mode
- `VectorStoreLongTermMemory`: Delegates to ingestion/search services
- `NoOpLongTermMemory`: Silent fallback when no vector store is configured

**Design Patterns:**
- **Facade**: `MemoryService` unifies two distinct memory systems
- **Strategy**: Multiple implementations (`Redis`, `InMemory`, `NoOp`) selected via Spring conditionals
- **Null Object**: `NoOpLongTermMemory` avoids null checks throughout codebase

### 4.5 Vector/RAG Module (`com.agenthub.memory.vector` + `embedding`)

**Responsibility:** Document ingestion, embedding generation, and semantic search.

**Key Classes:**
- `DocumentIngestionService`: Chunks text → embeds → stores in vector DB
- `SemanticSearchService`: Embeds query → searches vector store → returns ranked results
- `InMemoryVectorStore`: Cosine-similarity search over in-memory embeddings
- `OllamaEmbeddingService`: Generates embeddings via Ollama's `/api/embeddings` endpoint
- `VectorSearchResult`: Record with id, content, similarity score

**Design Patterns:**
- **Pipeline**: Ingest = chunk → embed → store; Search = embed → search → rank
- **Strategy**: `VectorStore` and `EmbeddingService` interfaces allow swapping backends

### 4.6 Tools Module (`com.agenthub.tools`)

**Responsibility:** Extensible tool execution with LLM-driven selection.

**Key Classes:**
- `Tool` (interface): `getName()`, `getDescription()`, `execute(Map<String, Object>)`
- `ToolRegistry`: Auto-discovers tools via Spring DI, provides execution with error handling
- `ToolSelectionService`: Prompts LLM to choose a tool and extract parameters from user input
- `ToolExecutionPipeline`: Full pipeline: select → execute → return result
- `FileReaderTool`: Reads files with path-traversal protection
- `HttpClientTool`: HTTP requests with domain allowlisting

**Design Patterns:**
- **Command**: Each `Tool` encapsulates an action with parameterized execution
- **Chain of Responsibility**: Pipeline flows from selection to execution to result
- **Service Locator**: `ToolRegistry` resolves tools by name

### 4.7 Prompt Module (`com.agenthub.prompt`)

**Responsibility:** Load, store, and render prompt templates with variable substitution.

**Key Classes:**
- `PromptEngine` (interface): Template management and rendering
- `DefaultPromptEngine`: `{{variable}}` substitution with fallback templates
- `PromptTemplateLoader`: Loads `.txt` files from `classpath:prompts/`

**Design Patterns:**
- **Template Method**: Prompt templates define the structure; variables are injected at runtime
- **Registry**: Templates loaded at startup and cached in `ConcurrentHashMap`

---

## 5. Critical Workflows

### Workflow 1: User Chat Message (Request Lifecycle)

```
User types "Review this code for security issues" in UI
  → Frontend: api.executeAgent("CodeReview", input, sessionId)
  → HTTP POST /api/agents/execute
  → AgentController validates @NotBlank constraints
  → AgentService.executeAgent():
      → Persists user message to DB (MessageEntity)
      → Orchestrator.execute(request)
      → AgentRegistry.requireAgent("CodeReview") → CodeReviewAgent
      → AbstractAgent.execute():
          → memoryService.getShortTermMemory(sessionId) → recent conversation
          → memoryService.retrieveRelevantContext(input, 3) → related knowledge
          → agent.buildPrompt() → rendered template with memory context
          → llmClient.generate(prompt, options) → Ollama HTTP call
          → memoryService.storeShortTermMemory(sessionId, interaction)
      → Returns AgentResponse(output, latencyMs, success=true)
      → Persists assistant message to DB
  → HTTP 200 JSON response
  → Frontend renders assistant message in chat
```

### Workflow 2: RAG Document Ingestion

```
POST /api/knowledge/ingest { content: "...", documentId: "doc-1" }
  → KnowledgeController.ingest()
  → DocumentIngestionService.ingest():
      → chunkText(content, 500, 50) → splits into overlapping chunks
      → For each chunk:
          → OllamaEmbeddingService.embed(chunk) → float[] embedding
          → InMemoryVectorStore.store(chunkId, chunk, embedding)
      → Returns number of chunks stored
  → HTTP 200 { documentId, chunks, success }
```

### Workflow 3: DAG Parallel Execution

```
POST /api/workflow/execute with nodes: [plan, optimize, review(depends: plan, optimize)]
  → WorkflowController builds DAG via DAG.builder()
  → DAG.topologicalOrder() → ["plan", "optimize", "review"]
  → DAGExecutorService.execute():
      → "plan" node → submitted to thread pool (no deps)
      → "optimize" node → submitted to thread pool (no deps)
      → "review" node → waits on CompletableFuture of plan + optimize
      → Both complete → "review" executes with combined outputs
  → Aggregated DAGExecutionResult returned
```

---

## 6. Performance Considerations

### Latency-Sensitive Areas

1. **LLM inference**: Ollama calls dominate total latency (1-10s per call depending on model/hardware)
2. **Embedding generation**: Each document chunk requires a separate embedding API call
3. **Vector search**: Cosine similarity over all stored vectors (O(n) for in-memory store)
4. **Agent chaining**: Sequential chains multiply latency linearly with chain length

### Bottlenecks

- **Single-threaded LLM**: Ollama processes one request at a time per model (unless using multiple GPUs)
- **In-memory vector store**: Linear scan for similarity — O(n) per search
- **Database writes**: Every message is persisted synchronously before returning

### Optimizations Used

- **Async execution**: DAG nodes run in parallel via `ExecutorService` thread pool
- **WebClient non-blocking**: Ollama calls use reactive WebClient (no thread blocking during I/O)
- **Connection pooling**: Spring Data JPA + HikariCP for database connections
- **ConcurrentHashMap**: Lock-free reads for agent/tool/template registries
- **Memory TTL**: Redis keys expire after 60 minutes to prevent unbounded growth
- **Streaming**: SSE streaming returns tokens as they're generated (perceived latency improvement)

### Possible Optimizations

- Replace in-memory vector store with HNSW-indexed store (O(log n) search)
- Batch embedding calls (Ollama doesn't natively support this yet)
- Add response caching for identical prompts
- Pre-warm LLM model on startup to avoid cold-start latency

---

## 7. Scalability Design

### Current Architecture (Single Instance)

- Stateful: In-memory vector store and short-term memory are node-local
- Suitable for development and single-user scenarios

### Horizontal Scaling Strategy

1. **Backend**: Stateless with externalized state (Redis + PostgreSQL + ChromaDB)
   - Multiple backend instances behind a load balancer
   - Session affinity not required (all state in external stores)

2. **Redis**: Used as shared short-term memory across instances
   - Supports Redis Cluster for partitioning

3. **PostgreSQL**: Standard primary-replica setup
   - Read replicas for session history queries
   - Connection pooling via PgBouncer

4. **Ollama**: Multiple Ollama instances with round-robin routing
   - Each instance loads a model into GPU VRAM
   - Separate instances for generation vs. embedding

5. **Vector Store**: Replace InMemoryVectorStore with distributed store (Qdrant, Weaviate)
   - Supports sharding and replication natively

### Stateless/Stateful Decisions

| Component | Stateless? | Rationale |
|-----------|-----------|-----------|
| Backend (Spring Boot) | Yes (with Redis/PG) | All state externalized |
| AgentRegistry | Yes | Populated from code at startup — identical across instances |
| PromptEngine | Yes | Templates loaded from classpath — identical across instances |
| In-memory vector store | No (dev only) | Production uses external ChromaDB |
| InMemoryShortTermMemory | No (dev only) | Production uses Redis |

### Caching Strategy

- **Prompt templates**: Loaded once at startup, cached in-memory
- **Agent resolution**: ConcurrentHashMap with alias caching
- **No LLM response caching**: Intentional — responses should vary for same input (temperature > 0)

---

## 8. Failure Handling

### Edge Cases

- **Ollama unavailable**: `LLMException` propagated, circuit breaker opens after 5 failures, returns 503
- **Redis unavailable (default profile)**: App fails to start (critical dependency)
- **Redis unavailable (dev profile)**: Gracefully uses `InMemoryShortTermMemory`
- **Vector store unavailable**: `SemanticSearchService` returns empty results (no-op mode)
- **Agent not found**: `AgentNotFoundException` → 404 response
- **Tool selection fails**: Returns "no tool used" result; agent continues without tool output
- **DAG cycle detected**: `DAG.builder()` throws `IllegalArgumentException` at build time
- **DAG node timeout**: `TimeoutException` caught, partial results returned

### Retry Mechanisms

- **Resilience4j `@Retry`** on `OllamaLLMClient.generate()`:
  - Max 3 attempts
  - 2-second wait between retries
  - Retries on `LLMException` and `TimeoutException`
- **Circuit Breaker** on LLM calls:
  - Sliding window: 10 calls
  - Failure threshold: 50%
  - Open state duration: 30 seconds
  - Half-open: allows 3 test calls
- **Time Limiter**: 120-second timeout per LLM call

### Error Handling Strategy

1. **Controller layer**: `GlobalExceptionHandler` maps exceptions to HTTP status codes
2. **Service layer**: Business exceptions (`AgentNotFoundException`, `IllegalArgumentException`)
3. **Agent layer**: `AbstractAgent.execute()` catches all exceptions, returns `AgentResponse.failure()`
4. **Memory layer**: All memory operations wrapped in try-catch, failures logged as warnings (non-fatal)
5. **Tool layer**: `ToolExecutionException` wrapped with tool name and context

---

## 9. Testing Strategy

### Unit Tests (121 tests, all passing)

| Module | Test Class | Coverage |
|--------|-----------|----------|
| Agent | `AgentRegistryTest` | Registration, resolution, alias mapping |
| Agent | `ActionAgentTest` | Tool pipeline integration, prompt building |
| Agent | `CodeReviewAgentTest`, `PromptOptimizerAgentTest`, `TaskPlannerAgentTest`, `KnowledgeRetrievalAgentTest` | Prompt rendering, memory retrieval |
| LLM | `OllamaLLMClientTest` | HTTP interactions via MockWebServer |
| LLM | `OllamaLLMClientStreamTest` | SSE streaming parsing |
| Memory | `InMemoryShortTermMemoryTest` | Store, retrieve, TTL, max history |
| Memory | `DocumentIngestionServiceTest` | Chunking, embedding, storage |
| Memory | `InMemoryVectorStoreTest` | Cosine similarity, CRUD |
| Memory | `SemanticSearchServiceTest` | Search flow, no-op mode |
| Orchestrator | `DefaultAgentOrchestratorTest` | Single exec, chain exec |
| Orchestrator | `DAGExecutorServiceTest` | Parallel execution, timeouts |
| Orchestrator | `DAGTest`, `DAGNodeTest` | Graph construction, topological sort, cycle detection |
| Prompt | `DefaultPromptEngineTest` | Template rendering, variable substitution |
| Tools | `FileReaderToolTest` | Path traversal protection, file reading |
| Tools | `ToolExecutionPipelineTest` | End-to-end pipeline |
| Tools | `ToolRegistryTest` | Registration, execution, error handling |
| Tools | `ToolSelectionServiceTest` | LLM response parsing, JSON extraction |

### Test Approach

- **Mocking**: LLM calls mocked via MockWebServer (OkHttp) for deterministic tests
- **No Spring context**: Most tests are pure unit tests (no `@SpringBootTest`)
- **Edge cases**: Null inputs, empty results, timeouts, malformed responses

### Gaps

- No integration tests with real Ollama (would require test containers or CI with GPU)
- No end-to-end tests (frontend → backend → Ollama)
- No load/stress tests for concurrent DAG execution
- Controller layer tested only via exception handler mapping (no MockMvc tests)

---

## 10. Deployment Model

### Local Development

```bash
ollama serve
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm run dev
```

### Docker Compose (Production-Like)

```yaml
Services: postgres, redis, chromadb, ollama, backend, frontend
Networking: Docker internal DNS (service names as hostnames)
Health checks: postgres (pg_isready), redis (redis-cli ping), backend (wget /api/health)
Volumes: pgdata, redisdata, chromadata, ollamadata (persistent across restarts)
```

### Container Architecture

- **Backend**: Multi-stage build (Maven build → JRE runtime), runs as non-root user
- **Frontend**: Multi-stage build (npm build → nginx static serving), reverse proxy to backend
- **Resource limits**: Ollama container reserves 4GB memory for model loading

### CI/CD

- GitHub Actions workflow located at `.github/java-upgrade/` (upgrade automation)
- Build verification: `mvn clean test` (121 tests)
- Docker build verification: `docker compose build`

---

## 11. Interview-Focused Q&A

### "Explain this project in 1 minute"

> AgentHub is a multi-agent AI orchestration platform. It routes user requests to specialized AI agents — like a code reviewer, task planner, or knowledge retrieval system — and can chain them together or run them in parallel via DAG workflows. Each agent uses a templated prompt, retrieves conversation history from Redis and relevant knowledge from a vector store, then calls a local LLM via Ollama. The whole system is observable with Prometheus metrics, resilient with retry/circuit breakers, and deployable via Docker Compose with PostgreSQL, Redis, ChromaDB, and Ollama.

### "Biggest challenges faced"

1. **Memory architecture design**: Balancing between short-term (session context) and long-term (knowledge base) memory required careful interface design to support multiple backends (Redis, in-memory, ChromaDB, vector store) with graceful fallbacks.

2. **DAG execution with proper dependency resolution**: Implementing parallel execution where nodes wait only on their specific dependencies — not a global barrier — required careful use of CompletableFuture chains and topological sorting.

3. **LLM reliability**: Local LLMs are non-deterministic and can timeout or return malformed responses. Resilience4j retry + circuit breaker patterns were essential to make the system robust.

4. **Tool selection via LLM**: Getting the LLM to reliably output structured JSON for tool selection required careful prompt engineering and fallback parsing logic.

### "What would you improve?"

1. **Replace in-memory vector store** with a proper HNSW-indexed store (Qdrant) for O(log n) search
2. **Add authentication** — currently all sessions are attributed to "default" user
3. **Implement agent evaluation** — track output quality metrics per agent over time
4. **Add WebSocket support** for bidirectional streaming (SSE is unidirectional)
5. **Add response caching** for deterministic prompts (temperature=0 queries)

### "How would you scale this to 1M users?"

1. **Horizontal backend scaling**: Deploy 10-50 backend instances behind ALB; state already externalized to Redis/PostgreSQL
2. **Redis Cluster**: Partition short-term memory across 6+ Redis nodes
3. **PostgreSQL**: Primary + 3 read replicas for session queries; PgBouncer for connection pooling
4. **Ollama fleet**: 20+ GPU instances with model-specific routing (Mistral on some, CodeLlama on others)
5. **Vector store**: Migrate to Qdrant Cloud or Weaviate with sharding across 3+ nodes
6. **CDN**: Serve React frontend from CloudFront/Vercel
7. **Rate limiting**: Per-user rate limits (10 req/min free, 100 req/min paid)
8. **Queue-based execution**: For long-running DAG workflows, use RabbitMQ/SQS to decouple request acceptance from execution
9. **Caching**: Redis cache for frequently-asked prompts with identical parameters

### "Design trade-offs you made"

| Decision | Trade-off |
|----------|-----------|
| Local LLM (Ollama) vs Cloud API | Privacy & cost ↔ slower inference, limited model options |
| In-memory vector store for dev | Zero setup ↔ data lost on restart, O(n) search |
| Synchronous message persistence | Data integrity ↔ adds latency to every request |
| Template-based prompts | Simplicity & maintainability ↔ less dynamic than programmatic prompts |
| Single Ollama model for all agents | Simple deployment ↔ all agents share same capabilities |
| `@ConditionalOnProperty` for backends | Flexible deployment ↔ runtime behavior depends on config |
| Sequential chain (no branching) | Simplicity ↔ can't do conditional routing mid-chain |

---

## 12. Possible Extensions

1. **Agent Marketplace**: REST API for registering custom agents with prompt templates, enabling users to create their own agents without code changes

2. **Multi-Model Routing**: Route compute-heavy agents (CodeReview) to larger models (Llama 70B) and lightweight agents (PromptOptimizer) to smaller models (Phi-3)

3. **Conversation Branching**: Allow users to fork a conversation at any point, exploring alternative agent responses without losing the original thread

4. **Real-Time Collaboration**: Multiple users interacting with the same agent session simultaneously (WebSocket + CRDT for message ordering)

5. **Agent Feedback Loop**: Users rate agent outputs; low-rated outputs trigger prompt template refinement via PromptOptimizer agent

6. **Scheduled Workflows**: Cron-triggered DAG executions for recurring tasks (daily code reviews, weekly report generation)

7. **Plugin Architecture**: Hot-loadable tools via JAR classpath scanning or REST-registered external tool endpoints

8. **Observability Dashboard**: Grafana dashboards showing LLM latency P50/P95/P99, token usage per agent, error rates, and throughput

9. **Fine-Tuning Pipeline**: Collect high-quality agent outputs as training data for model fine-tuning, creating specialized smaller models per agent

10. **Multi-Tenant Isolation**: Namespace agents, sessions, and knowledge per tenant with row-level security in PostgreSQL
