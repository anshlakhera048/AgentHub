# AgentHub — Interview Preparation Reference

> **Audience:** Backend / System Design interviewers  
> **Depth:** Production-grade; covers design, implementation, trade-offs, and failure scenarios  
> **Last updated:** March 2026

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Tech Stack and Rationale](#3-tech-stack-and-rationale)
4. [Detailed Component Breakdown](#4-detailed-component-breakdown)
5. [Data Layer Design](#5-data-layer-design)
6. [Key Features and Implementation Details](#6-key-features-and-implementation-details)
7. [Scalability and Performance](#7-scalability-and-performance)
8. [Reliability and Fault Tolerance](#8-reliability-and-fault-tolerance)
9. [Security Considerations](#9-security-considerations)
10. [Observability](#10-observability)
11. [Deployment and DevOps](#11-deployment-and-devops)
12. [Trade-offs and Design Decisions](#12-trade-offs-and-design-decisions)
13. [Interview Questions and Answers](#13-interview-questions-and-answers)
14. [Elevator Pitch](#14-elevator-pitch)
15. [Resume Bullet Mapping](#15-resume-bullet-mapping)

---

## 1. Project Overview

### Problem Statement

Modern AI applications require routing user requests to specialized language-model agents, managing conversational context across sessions, executing multi-step reasoning workflows, and integrating external tools — all in a reliable, observable, and extensible way. Existing LLM frameworks either force a specific cloud provider, lack composability, or do not address production concerns like fault tolerance, persistence, and observability.

### Why This Was Built

AgentHub was built to answer a concrete engineering question: **How do you build a production-ready multi-agent AI platform from scratch, without depending on opaque third-party orchestration libraries?** The project targets organizations that want to run inference locally (privacy, cost, latency), need deep control over agent behavior, and want a clear separation between orchestration logic and LLM provider details.

Real-world relevance:
- Companies moving AI workloads on-premises for data governance reasons
- Teams that need custom agent pipelines that third-party SDKs cannot express
- Engineers who need to understand every failure point in an AI system (not a black box)

### Key Objectives and Success Criteria

| Objective | Success Criteria |
|---|---|
| Pluggable agent specialization | Each agent has isolated prompt engineering and parameter contracts |
| Multi-modal orchestration | Single, chained, parallel DAG, and streaming execution all work on the same core |
| Memory tiering | Short-term session memory (Redis) and long-term vector memory (ChromaDB / in-memory) both functional |
| Fault tolerance | Retry + circuit breaker on LLM; graceful degradation when vector store is unavailable |
| Observability | Every execution path emits Micrometer metrics; MDC-correlated logging end-to-end |
| Testability | All components unit-testable via interfaces; no hidden singletons |
| Profile-based environment switching | Dev (H2 + in-memory store) / Prod (PostgreSQL + Redis + ChromaDB) with zero code changes |

---

## 2. High-Level Architecture

### System Design Explanation

AgentHub is a **layered, event-driven, multi-agent orchestration system** exposing a REST + SSE API. The core design separates concerns into six self-contained layers: HTTP (controllers), orchestration (agent routing and DAG execution), agent logic (prompt assembly and LLM invocation), memory (short-term and long-term), tools (file, HTTP, code execution), and infrastructure (LLM client, persistence, observability).

### Components and Their Responsibilities

| Component | Responsibility |
|---|---|
| **REST Controllers** | Accept requests, validate input, delegate to services, serialize responses |
| **AgentOrchestrator** | Routes requests: single-agent, chain, async, DAG workflow, or streaming |
| **AgentRegistry** | Thread-safe map of agent name → agent bean; alias resolution |
| **AbstractAgent + Impls** | Template-method base class; each subclass specializes prompt construction |
| **PromptEngine** | Loads `.txt` prompt templates; renders `{{variable}}` placeholders |
| **LLMClient** | Provider-agnostic interface; Ollama implementation with retry and circuit breaker |
| **MemoryService** | Facade over short-term (Redis/in-memory) and long-term (ChromaDB/vector) memory |
| **VectorStore + Embedding** | In-memory cosine-similarity store; nomic-embed-text embeddings via Ollama |
| **ToolRegistry + Pipeline** | LLM-driven tool selection → execution → result injection into agent context |
| **DAGExecutorService** | Parallel DAG execution; topological sort; `CompletableFuture` per node |
| **Persistence** (JPA) | Session and message persistence to PostgreSQL via Spring Data JPA |
| **Frontend** (React + Nginx) | SPA UI with agent selection, chat interface, session management; proxies `/api/*` to backend |

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client (Browser / API)                       │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTP / SSE
┌──────────────────────────────▼──────────────────────────────────────┐
│                  React SPA (Nginx :3000)                            │
│       AgentSelector │ ChatInterface │ SessionPanel                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ /api/* proxy
┌──────────────────────────────▼──────────────────────────────────────┐
│              Spring Boot Backend (:8080)                            │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  REST Controllers (MDC filter → requestId)                  │   │
│  │  AgentController │ StreamController │ WorkflowController    │   │
│  │  SessionController │ KnowledgeController │ HealthController  │   │
│  └───────────────────────────┬─────────────────────────────────┘   │
│                              │                                      │
│  ┌───────────────────────────▼─────────────────────────────────┐   │
│  │              DefaultAgentOrchestrator                        │   │
│  │  single │ chain │ async (@Async) │ stream (Flux) │ DAG      │   │
│  └──────┬───────────────┬────────────────────────┬────────────┘   │
│         │               │                        │                 │
│  ┌──────▼──────┐  ┌──────▼──────┐   ┌────────────▼──────────┐    │
│  │ AgentRegistry│  │  DAGExec-   │   │   StreamController    │    │
│  │ (ConcurrentH │  │  utorService│   │   Flux<String> SSE    │    │
│  │  ashMap)     │  │  (parallel) │   └───────────────────────┘    │
│  └──────┬───────┘  └──────┬──────┘                                │
│         │                 │                                        │
│  ┌──────▼─────────────────▼──────────────────────────────────┐   │
│  │                  AbstractAgent                             │   │
│  │  retrieveMemory → buildPrompt → callLLM → storeMemory     │   │
│  └──────┬─────────────────┬───────────────────┬──────────────┘   │
│         │                 │                   │                   │
│  ┌──────▼──────┐  ┌───────▼──────┐  ┌────────▼──────────────┐   │
│  │MemoryService│  │PromptEngine  │  │  OllamaLLMClient       │   │
│  │ ShortTerm   │  │Templates.txt │  │  @Retry @CircuitBreaker │   │
│  │ LongTerm    │  │ {{vars}}     │  │  /api/generate         │   │
│  └──────┬──────┘  └──────────────┘  └────────────────────────┘   │
│         │                                                          │
│  ┌──────▼───────────────────────────────────────────────────┐    │
│  │  Memory Implementations (conditional)                     │    │
│  │  Redis (prod) │ InMemory (dev) │ ChromaDB │ VectorStore   │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────┐     │
│  │  Persistence: JPA → PostgreSQL (prod) / H2 (dev)         │     │
│  │  Entities: agents │ sessions │ messages (Flyway V1)       │     │
│  └──────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘

External Services
  Ollama (:11434)    — LLM inference (Mistral) + embeddings (nomic-embed-text)
  PostgreSQL (:5432) — Sessions, messages, agent catalog
  Redis (:6379)      — Short-term session memory (FIFO list, 60-min TTL)
  ChromaDB (:8000)   — Long-term vector memory (production RAG store)
```

### Data Flow — Step-by-Step Request Lifecycle

**Single Agent Execution (`POST /api/agents/execute`):**

```
1. HTTP request arrives → MDCRequestFilter injects requestId into MDC
2. AgentController validates request (@Valid @NotBlank) → delegates to AgentService
3. AgentService creates/resolves SessionEntity → persists user MessageEntity
4. AgentOrchestrator.execute() → checks chainAgents
5. AgentRegistry.requireAgent("TaskPlanner") → resolves alias → returns bean
6. AbstractAgent.execute():
   a. ShortTermMemory.retrieve(sessionId) → last N messages from Redis List
   b. LongTermMemory.retrieve(query, topK) → embed query → cosine search → top-K chunks
   c. PromptEngine.getTemplate("TaskPlanner") → load TaskPlanner.txt
   d. PromptEngine.render(template, vars) → substitute {{memory_context}}, {{user_input}}, etc.
   e. OllamaLLMClient.generate(prompt, options) → POST /api/generate → await response
      [Resilience4j: retry up to 3× on LLMException, circuit breaker at 50% failure rate]
   f. ShortTermMemory.store(sessionId, response) → RPUSH + LTRIM in Redis
7. AgentService persists assistant MessageEntity
8. AgentResponse(output, latencyMs, requestId) returned → serialized as JSON
```

---

## 3. Tech Stack and Rationale

### Java 17 / Spring Boot 3.2.4

| | |
|---|---|
| **What it is** | Enterprise JVM backend framework |
| **Why chosen** | Mature DI container, production-grade Spring Data JPA, native WebFlux for reactive streams, strong Micrometer/Actuator ecosystem, @ConditionalOn* for profile-based wiring |
| **Trade-offs** | Higher startup time than Micronaut/Quarkus; heavier memory footprint than Go |
| **Alternatives** | Quarkus (faster startup, better GraalVM), Micronaut (AOT-friendly), FastAPI (Python, simpler but not JVM); Spring chosen for ecosystem maturity and team familiarity |

### Spring WebFlux + Project Reactor

| | |
|---|---|
| **What it is** | Non-blocking reactive programming model on Netty |
| **Why chosen** | SSE streaming requires `Flux<String>` without blocking threads; WebClient is naturally non-blocking for Ollama calls |
| **Trade-offs** | Steep learning curve; harder to debug with stack traces; mixing blocking JPA calls requires `subscribeOn(Schedulers.boundedElastic())` |
| **Alternatives** | Virtual threads (Java 21) could simplify blocking model; chosen WebFlux because existing HTTP client ecosystem targets it |

### Ollama (Local LLM Inference)

| | |
|---|---|
| **What it is** | Self-hosted LLM inference server (supports Mistral, Llama, Gemma, etc.) |
| **Why chosen** | No cloud API costs; data stays on-premises; supports both generation and embedding in one service |
| **Trade-offs** | Requires GPU/high-RAM host; model loading latency on cold start; no auto-scaling |
| **Alternatives** | OpenAI API (managed, costly, data leaves premises), HuggingFace TGI (complex ops), vLLM (higher throughput but more complex setup) |

### PostgreSQL 16

| | |
|---|---|
| **What it is** | ACID-compliant relational database |
| **Why chosen** | Sessions and messages are relational with FK constraints and ordering requirements; Hibernate `validate` mode with Flyway ensures schema correctness |
| **Trade-offs** | Requires separate container; overkill for dev (H2 used instead) |
| **Alternatives** | MySQL (less advanced JSON/UUID support), MongoDB (schemaless but no FK guarantees for session integrity) |

### Flyway

| | |
|---|---|
| **What it is** | Database migration tool |
| **Why chosen** | Versioned SQL migrations (`V1__init_schema.sql`) guarantee schema reproducibility across environments |
| **Trade-offs** | Migrations must be carefully managed — irreversible once applied |
| **Alternatives** | Liquibase (more complex, XML-heavy), Hibernate `create-drop` (dev only, no audit trail) |

### Redis 7

| | |
|---|---|
| **What it is** | In-memory data structure store; used for short-term conversation memory |
| **Why chosen** | Native List datatype maps exactly to FIFO conversation history (`RPUSH` + `LTRIM`); built-in TTL auto-expires stale sessions; sub-millisecond latency |
| **Trade-offs** | Memory-limited; data loss on restart (acceptable for ephemeral session context); adds operational dependency |
| **Alternatives** | Memcached (no TTL per key, no list type), PostgreSQL (durable but slow for high-frequency reads) |

### ChromaDB

| | |
|---|---|
| **What it is** | Open-source vector database for semantic search |
| **Why chosen** | Simple REST API; easy local deployment; supports metadata filtering; ideal for RAG knowledge base |
| **Trade-offs** | No built-in clustering; not a general-purpose database; persistence requires volume mounts |
| **Alternatives** | Pinecone (managed, paid), Weaviate (more complex), pgvector (PostgreSQL extension — avoids separate service but less feature-rich) |

### Resilience4j 2.2

| | |
|---|---|
| **What it is** | Fault tolerance library (retry, circuit breaker, bulkhead, rate limiter) |
| **Why chosen** | Annotation-based (`@Retry`, `@CircuitBreaker`) on `OllamaLLMClient`; zero-cost abstraction; integrates with Micrometer |
| **Trade-offs** | Configuration via application.yml only (no dynamic update at runtime without restart) |
| **Alternatives** | Spring Retry (simpler but no circuit breaker), Hystrix (Netflix, now in maintenance mode) |

### Micrometer + Prometheus

| | |
|---|---|
| **What it is** | Vendor-neutral metrics façade; Prometheus scrapes `/actuator/prometheus` |
| **Why chosen** | Native integration with Spring Boot Actuator; timers, counters, and gauges with tag-based dimensionality |
| **Trade-offs** | Pull-based scraping requires Prometheus to be up; push-based alternatives (StatsD) easier in some networks |
| **Alternatives** | Dropwizard Metrics (older, less Spring-native), OpenTelemetry (broader but more complex setup) |

### React 18 + Vite

| | |
|---|---|
| **What it is** | SPA frontend with fast HMR build tool |
| **Why chosen** | Lightweight for a chat UI; Vite offers near-instant dev reloads; `localStorage` persistence for session state |
| **Trade-offs** | SPA requires Nginx `try_files` fallback; no SSR (not needed for internal tooling) |
| **Alternatives** | Next.js (SSR, more complex), Angular (heavier), plain HTML (fine for demo but not maintainable) |

---

## 4. Detailed Component Breakdown

### 4.1 Agent Layer

#### `AbstractAgent` — Template Method Pattern

```
AbstractAgent.execute(AgentRequest):
  1. retrieveMemoryContext()     ← short-term history + long-term semantic chunks
  2. buildPrompt(request, ctx)   ← abstract; each subclass specializes
  3. callLLM(prompt, options)    ← OllamaLLMClient with retry
  4. storeInMemory(session, res) ← write back to short-term store
  → AgentResponse.success() | AgentResponse.failure()
```

All concrete agents override `buildPrompt()` only — keeping LLM retry, memory integration, and error handling in the base class. This is a deliberate application of the **Template Method Pattern** to avoid code duplication while allowing per-agent prompt specialization.

#### Concrete Agents

| Agent | Key Behavior | Special Parameters |
|---|---|---|
| `TaskPlannerAgent` | Decomposes tasks into phased plans with effort estimates and dependency mapping | `complexity` (low/medium/high) |
| `CodeReviewAgent` | Structured severity-based review: bugs, security, performance, best practices | `language` (auto-detect), `focus` (multi-value) |
| `KnowledgeRetrievalAgent` | Overrides `retrieveMemoryContext()` to skip short-term; performs RAG via `SemanticSearchService` before prompt render | `topK` (default 5), `style` |
| `PromptOptimizerAgent` | Analyzes user prompt clarity; rewrites for specificity and intent | `goal` (default: clarity and effectiveness) |
| `ActionAgent` | Invokes `ToolExecutionPipeline` before prompt build; injects tool output and tool metadata into prompt context | — |

#### `AgentRegistry`

- Backed by `ConcurrentHashMap<String, Agent>` — thread-safe for concurrent requests
- Spring injects `List<Agent>` beans at startup; registry self-populates
- **Alias resolution order:** exact match → lowercase normalized → kebab-case conversion → fail with `AgentNotFoundException`
- `requireAgent()` throws 404-mapped exception; `findAgent()` returns `Optional`

#### Concurrency Model

- Multiple requests share agent bean instances — agents are **stateless singletons**
- No mutable agent-level state; all state is per-request inside `execute()` local variables
- Memory reads/writes are delegated to thread-safe stores (Redis, `ConcurrentHashMap`)

---

### 4.2 LLM Client Layer

#### `OllamaLLMClient`

```java
// Blocking generation
generate(prompt, options) → POST /api/generate { stream: false }
                          → WebClient.post().bodyValue(request)
                          → .block(Duration.ofSeconds(120))
                          → extract root.path("response")

// Reactive streaming
stream(prompt, options) → POST /api/generate { stream: true }
                        → Flux<String> of NDJSON chunks
                        → filter(done=true) → mapNotNull(response field)
```

**Fault Tolerance:**
- `@Retry(name="llmClient")`: 3 attempts, 2-second fixed backoff, retries `LLMException` and `TimeoutException`
- `@CircuitBreaker(name="llmClient")`: 10-call sliding window, 50% failure threshold, 30s open cooldown, 3 half-open probe calls
- LLM timeouts wrapped in `LLMException` before circuit breaker sees them

**`LLMOptions` record:**
```
model           (default: mistral)
temperature     (default: 0.7)
maxTokens       (default: 2048)
topP            (default: 0.9)
timeoutSeconds  (default: 120)
```
Accepts overrides from `AgentRequest.parameters` — callers can specify `model=llama3` or `temperature=0.2` per request.

---

### 4.3 Orchestrator

#### `DefaultAgentOrchestrator` — Routing Logic

```
execute(request):
  if request.chainAgents() non-empty → executeChain()
  else                               → single agent via AgentRegistry

executeChain(request):
  chain = [primaryAgent] + request.chainAgents()
  validate chain depth  ≤ MAX_CHAIN_DEPTH (10)
  loop: for each agent in chain
        agentRequest.input = previous step output
        result = agent.execute(agentRequest)
        accumulate into chainResults[]
        fail-fast on error

executeAsync(request):
  @Async("agentExecutor") wraps execute() in CompletableFuture

executeStream(request):
  retrieve memory → build prompt → LLMClient.stream() → Flux<String>
  onComplete: storeInMemory()

executeWorkflow(request):
  DAG.Builder.build(nodes) → DAGExecutorService.execute()
```

**Thread Pools:**
- `agentExecutor`: core=4, max=16, queue=100, prefix `agent-exec-`
- `llmExecutor`: core=2, max=8, queue=50, prefix `llm-exec-`
- `dag-exec-{id}`: fixed pool of 8 daemon threads per DAG execution

---

### 4.4 DAG Execution Engine

#### `DAG` — Immutable Graph Value Object

```
Builder.addNode(id, agentName, dependencies, parameters)
Builder.build():
  1. Detect duplicate node IDs → throw
  2. Detect missing dependencies → throw
  3. Cycle detection via Kahn's algorithm (in-degree BFS)
     → if processed count < total nodes → cycle exists → throw
  4. Verify at least one root node
  → returns immutable DAG
```

#### `DAGExecutorService` — Parallel Execution Algorithm

```
execute(dag, input, sessionId):
  topologicalOrder = dag.topologicalOrder()           // Kahn's BFS
  futures = new ConcurrentHashMap<String, Future>     // node → future

  for each nodeId in topologicalOrder:
    deps = node.dependencies()
    depFutures = deps.map(id → futures.get(id))

    future = CompletableFuture.allOf(depFutures)
      .thenApplyAsync(() → {
          if any dep failed → return DAGNodeResult.failure("Skipped: dep X failed")
          nodeInput = buildNodeInput(rootInput, depOutputs)
          start = now()
          result = agentRegistry.requireAgent(agentName).execute(agentRequest)
          latency = now() - start
          return DAGNodeResult.success(result.output, latency)
      }, dagExecutorService)

    futures.put(nodeId, future)

  CompletableFuture.allOf(all futures).get(timeout)
  → DAGExecutionResult { executionId, nodeResults, totalLatencyMs }
```

**Key properties:**
- Nodes with no shared dependencies execute in **true parallel** on `dag-exec-*` threads
- Dependency failure **cascades**: all transitive dependents are marked `Skipped`, not `Failed`
- Global timeout: `nodeTimeoutSeconds * dag.size()` (prevents indefinite hangs)
- MDC: `dagExecutionId` + `dagNodeId` propagated into each thread for correlated logging

---

### 4.5 Memory System

#### Tiered Memory Architecture

```
MemoryService (facade)
  ├── ShortTermMemory      ← session-scoped recent conversation history
  │   ├── RedisShortTermMemory    (prod: key agenthub:memory:session:{id})
  │   └── InMemoryShortTermMemory (dev: ConcurrentHashMap<sessionId, List<String>>)
  │
  └── LongTermMemory       ← semantic knowledge base for RAG
      ├── ChromaLongTermMemory           (prod: ChromaDB REST API)
      ├── VectorStoreLongTermMemory      (dev: InMemoryVectorStore)
      └── NoOpLongTermMemory             (fallback: all operations silent)
```

**Redis Short-Term Memory:**
- Key: `agenthub:memory:session:{sessionId}` (Redis List)
- `RPUSH` appends new message; `LTRIM 0 maxHistory-1` enforces rolling window
- `EXPIRE` resets TTL (60 min default) on every write
- Retrieval: `LRANGE 0 -1` → join as newline-delimited context string

**In-Memory Vector Store (dev):**
- `ConcurrentHashMap<String, StoredEntry>` of `{id, content, float[] embedding}`
- Cosine similarity computed element-wise; no external library dependency
- Results sorted descending by score; top-K returned
- Supports prefix-based chunk deletion: `id → id_chunk_0, id_chunk_1, ...`

---

### 4.6 Tool System

#### Architecture

```
ActionAgent.execute()
  → ToolExecutionPipeline.execute(input)
      → ToolSelectionService.selectTool(input)   [LLM call]
          → build selection prompt (tool catalog)
          → OllamaLLMClient.generate(selectionPrompt)
          → extract JSON { toolRequired, toolName, parameters, reasoning }
          → validate toolName exists in registry
          → ToolSelection.none() on parse failure
      → if toolRequired:
          ToolRegistry.executeTool(toolName, parameters)
          → Tool.execute(Map<String,Object>)
          → ToolExecutionResult { output, latencyMs, success }
      → return ToolExecutionResult (used in ActionAgent.buildPrompt())
```

**Tool Implementations:**

| Tool | Security Controls |
|---|---|
| `FileReaderTool` | `Path.normalize().startsWith(basePath)` prevents traversal; 1 MB max file size |
| `HttpClientTool` | Domain allowlist configurable; 10s connect / 30s request timeout |
| `CodeExecutionTool` | **Disabled by default**; static code validation before subprocess spawn; `waitFor(timeout)` kill |

---

### 4.7 Prompt Engineering

#### Template System

- Templates stored as `classpath:prompts/{AgentName}.txt`
- Loaded at `@PostConstruct` into `ConcurrentHashMap<String, String>` (thread-safe once initialized)
- Placeholder syntax: `{{variable_name}}` — replaced by `String.replace()` iteration over `Map.entrySet()`
- Unresolved placeholders stripped: `replaceAll("\\{\\{[^}]+\\}\\}", "")`
- Dynamic templates: `registerTemplate(name, content)` allows runtime injection (used in tests)

#### Context Augmentation

```
augmentWithContext(template, memoryContext):
  → prepends "CONVERSATION HISTORY:\n{context}\n\n" before main template

augmentWithExamples(template, examples):
  → appends "EXAMPLES:\n{examples}" after main template
```

---

## 5. Data Layer Design

### Database Schema

**Managed by Flyway** (`V1__init_schema.sql`) in production, H2 `create-drop` in dev.

```sql
-- Agent catalog (seeded at startup)
CREATE TABLE agents (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(255) NOT NULL UNIQUE,
  description VARCHAR(1024),
  prompt_template TEXT,
  enabled     BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ
);

-- Conversation containers
CREATE TABLE sessions (
  id         UUID PRIMARY KEY,
  user_id    VARCHAR(255),
  agent_name VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);

-- Ordered conversation history
CREATE TABLE messages (
  id         UUID PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  role       VARCHAR(20) NOT NULL,  -- 'user' | 'assistant'
  content    TEXT NOT NULL,
  agent_name VARCHAR(255),
  latency_ms BIGINT,
  timestamp  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_messages_session_id ON messages(session_id);
```

### Indexing Strategy

| Index | Column | Rationale |
|---|---|---|
| `idx_sessions_user_id` | `sessions.user_id` | `GET /api/sessions?userId=` queries this column; without index = full table scan |
| `idx_messages_session_id` | `messages.session_id` | All message reads and deletes filter on this column; critical for chat history load |
| PK on all tables | UUID | Random UUIDs — no sequential hotspot unlike auto-increment IDs; UUID v4 from `gen_random_uuid()` |

**Note on UUID vs auto-increment:** UUIDs avoid sequential insert hotspots in distributed scenarios and make IDs globally unique across shards. Trade-off: larger index size; can be mitigated with UUID v7 (time-ordered) in future iterations.

### Consistency Model

- **ACID** for session and message writes — PostgreSQL transactions via Spring `@Transactional`
- **JPA Lifecycle hooks:** `@PrePersist` sets `createdAt`/`timestamp`; `@PreUpdate` refreshes `updatedAt` — no clock skew risk from application layer
- `ON DELETE CASCADE` on `messages.session_id` — deleting a session atomically removes all its messages
- Hibernate `validate` mode in production: Flyway owns schema; Hibernate only reads DDL — prevents accidental schema mutation

### Caching Strategy

| Layer | Mechanism | Scope |
|---|---|---|
| Short-term conversation context | Redis List (FIFO, TTL 60 min) | Per session ID |
| Semantic knowledge chunks | In-memory `ConcurrentHashMap` (dev) / ChromaDB (prod) | Global knowledge base |
| Prompt templates | `ConcurrentHashMap` in `DefaultPromptEngine` | Application-scoped (immutable after init) |
| Agent registry | `ConcurrentHashMap` in `AgentRegistry` | Application-scoped (static after startup) |

No HTTP-level caching (agent responses are LLM-generated, inherently non-deterministic and session-specific).

---

## 6. Key Features and Implementation Details

### 6.1 Multi-Mode Orchestration

**Single Agent:** Request goes directly to one agent bean. Low overhead, low latency.

**Agent Chaining (Sequential Pipeline):**
- Chain depth capped at `MAX_CHAIN_DEPTH = 10` — prevents infinite loops in misconfigured chains
- Each step's output becomes the next step's input — output context evolves through the chain
- Fails fast: first failure short-circuits the chain; all completed step results are preserved in `chainResults[]`
- Edge case: empty chain → falls back to single agent execution

**DAG Workflow:**
- Pure function: same input, same DAG, reproducible (deterministic) agent execution order
- Parallel branches execute concurrently; joining happens naturally via `CompletableFuture.allOf()`
- A diamond pattern (A → B, A → C, B+C → D) is expressible natively
- Edge cases handled: cycles rejected at build time, missing dependency nodes rejected, empty DAG rejected

**Streaming (SSE):**
- Bypasses the full `AbstractAgent` stack to avoid buffering
- Reads memory, builds prompt, then opens a `Flux<String>` directly to Ollama's NDJSON stream
- Client receives tokens as server-sent events; memory is written only on stream completion
- Edge case: if client disconnects mid-stream, Reactor cancels the subscription and the `onComplete` handler does not fire — memory is not stored for incomplete interactions

### 6.2 RAG (Retrieval-Augmented Generation)

**Ingestion Flow:**
```
POST /api/knowledge/ingest { content, documentId }
  → DocumentIngestionService.ingest(content, documentId)
      → chunkBySentenceBoundary(content, maxChunkSize=500, overlap=50)
          → split on (?<=[.!?])\s+ regex
          → accumulate sentences until chunk full
          → carry last 50 chars as overlap seed for next chunk
      → for each chunk:
          OllamaEmbeddingService.embed(chunk) → float[768]
          VectorStore.store("{docId}_chunk_{i}", chunk, embedding)
```

**Retrieval Flow (KnowledgeRetrievalAgent):**
```
SemanticSearchService.search(query, topK=5)
  → OllamaEmbeddingService.embed(query) → float[768]
  → InMemoryVectorStore.search(embedding, topK)
      → for each stored entry: cosineSimilarity(query, stored)
      → sort descending → top-K results
  → chunks injected into {{retrieved_context}} in KnowledgeRetrieval.txt
```

**Edge Cases:**
- Empty content ingest → rejected with 400
- Vector store unavailable → `SemanticSearchService.isAvailable()` check → gracefully returns empty context rather than failing
- Null `documentId` → auto-generated UUID
- Document deletion clears all chunks by prefix match (`id_chunk_*`)

### 6.3 LLM-Driven Tool Selection

The `ActionAgent` does not hardcode which tool to use. Instead, it delegates to `ToolSelectionService`, which:
1. Serializes all registered tool names and descriptions into a prompt
2. Asks the LLM: "Which tool should be used for this input? Respond with raw JSON only"
3. Parses the response — strips markdown fences via `extractJson()`, deserializes `{ toolRequired, toolName, parameters, reasoning }`
4. Validates `toolName` exists in `ToolRegistry`
5. Falls back to `ToolSelection.none()` (no tool) on any parse or validation failure — never crashes

**Failure modes handled:**
- LLM returns malformed JSON → `extractJson()` + `try/catch` on `ObjectMapper` → `ToolSelection.none()`
- LLM hallucinates a tool name → registry lookup fails → `ToolSelection.none()`
- Tool execution throws exception → `ToolExecutionException` → `ToolExecutionResult.failure()` → propagated as agent failure

### 6.4 Session Management

- `SessionEntity` created on `POST /api/sessions`
- Every `AgentController.execute()` call persists a `user` message and an `assistant` message atomically via `AgentService`
- `GET /api/sessions/{id}/history` returns messages ordered by `timestamp ASC` — reconstructs the conversation in order
- `DELETE /api/sessions/{id}` cascades: JPA `deleteBySessionId(UUID)` runs before deleting the session; then `SessionRepository.deleteById()` — consistent two-phase delete
- Short-term Redis memory is cleared separately via `ShortTermMemory.clear(sessionId)` to avoid orphaned Redis keys

### 6.5 Profile-Based Environment Switching

```yaml
# application.yml (production)
agenthub.memory.redis.enabled=true
agenthub.memory.chroma.enabled=true
agenthub.memory.vector.enabled=false

# application-dev.yml (development)
agenthub.memory.redis.enabled=false
agenthub.memory.chroma.enabled=false
agenthub.memory.vector.enabled=true
```

- `@ConditionalOnProperty` activates the correct bean implementation
- `@ConditionalOnMissingBean(LongTermMemory.class)` catches all gaps → `NoOpLongTermMemory` fallback
- Zero code changes between profiles; all switching is configuration-driven

---

## 7. Scalability and Performance

### Horizontal Scaling

| Component | Scalability Approach |
|---|---|
| **Backend** | Stateless Spring Boot instances — scale by adding replicas behind a load balancer; session state is externalized to Redis |
| **PostgreSQL** | Read replicas for history queries; connection pooling via HikariCP (Spring default) |
| **Redis** | Redis Cluster for shard-based horizontal scaling; Sentinel for HA |
| **ChromaDB** | Single-node (production constraint); replace with Weaviate or Pinecone for clustering |
| **Ollama** | GPU-bound; scale by spinning up additional Ollama instances and round-robin via load balancer on `OLLAMA_BASE_URL` |

### Vertical Scaling

- JVM configured with `-XX:MaxRAMPercentage=75.0` — automatically adjusts heap to container size
- ZGC (`-XX:+UseZGC`) for low-pause GC — critical for streaming endpoints where long GC pauses interrupt SSE
- DAG thread pool is configurable (`agenthub.dag.thread-pool-size`) — can be tuned to match available cores

### Bottlenecks and Optimizations

| Bottleneck | Mitigation |
|---|---|
| LLM inference latency (1–30s per call) | Streaming SSE returns tokens as generated; user sees progressive output |
| Embedding latency for RAG | Embeddings computed once at ingest; query embedding is the only hot path per request |
| DAG node serialization | Nodes with no shared deps run in parallel — the DAG model exploits this naturally |
| Redis List LRANGE on every request | Bounded by `maxHistoryLength=10`; fixed-size overhead |
| JPA flush on every message persist | Messages inserted individually per request — acceptable for current load; batch insert could be added |

### Throughput and Latency Considerations

- **P50 latency target:** < 5s for single agent (LLM-bound, not infrastructure-bound)
- **SSE streaming:** first token within ~500ms; total completion 5–30s depending on model and output length
- **DAG parallelism benefit:** A 3-node diamond DAG (A → B, A → C, B+C → D) takes `max(B, C) + D` time vs. `B + C + D` in a chain
- **LLM call limit:** `timeoutSeconds=120` prevents runaway requests from blocking thread pools indefinitely

### Load Handling

- Agent executor core=4, max=16; queue depth=100 — absorbs burst traffic before rejecting
- `@Async("agentExecutor")` for async endpoint — returns `CompletableFuture<AgentResponse>` immediately
- WebFlux SSE stream uses Netty's event loop — no thread blocked per connection

---

## 8. Reliability and Fault Tolerance

### Retry Mechanism

```yaml
resilience4j.retry.instances.llmClient:
  max-attempts: 3
  wait-duration: 2s
  retry-exceptions: com.agenthub.llm.LLMException, java.util.concurrent.TimeoutException
```

- Annotation: `@Retry(name="llmClient")` on `OllamaLLMClient.generate()` and `.stream()`
- Fixed backoff (2s) — intentional: jitter not added (single LLM instance, contention not a concern); could add `exponential-backoff-multiplier` for multi-instance Ollama

### Circuit Breaker

```yaml
resilience4j.circuitbreaker.instances.llmClient:
  sliding-window-type: COUNT_BASED
  sliding-window-size: 10
  failure-rate-threshold: 50        # open at 50% failure
  wait-duration-in-open-state: 30s
  permitted-number-of-calls-in-half-open-state: 3
```

- Opens when 5 of last 10 calls fail → prevents hammering an unhealthy Ollama instance
- After 30s cooldown: 3 probe calls in half-open state → if all pass, closes circuit
- Failure from an open circuit returns `LLMException` immediately (no retry attempted) → mapped to HTTP 503

### Idempotency

- `POST /api/knowledge/ingest` with the same `documentId`: document chunks stored with keys `{docId}_chunk_{i}` — re-ingestion overwrites existing chunks (idempotent by design)
- `POST /api/agents/execute`: not idempotent by nature (LLM responses are stochastic); callers should not resend on timeout — use `X-Request-ID` for deduplication at the API gateway level
- Session creation: POST creates a new UUID-identified session each time — idempotent at the entity level (UUIDs are unique)

### Failure Propagation in DAG

- Node failure does not crash the executor — it sets the future to a `DAGNodeResult.failure()` value
- Downstream nodes check dep results before executing → cascade as `Skipped: dependency 'X' failed`
- `DAGExecutionResult.success` is true only if **all** nodes succeed — caller gets a full picture of partial success

### At-Least-Once vs. Exactly-Once

- LLM calls are **at-least-once** (retry can re-invoke); LLM responses are non-deterministic so duplicate invocations are tolerated
- Message persistence is **at-most-once** per request (no idempotency key on `MessageEntity`); duplicate HTTP retry by a client could result in duplicate messages — mitigated by `X-Request-ID` correlation at the API gateway level
- No Kafka/message queue currently (synchronous request/response model); if async messaging were introduced, idempotency keys on `MessageEntity` would be required

---

## 9. Security Considerations

### Authentication and Authorization

- No auth implemented in the current scope — designed as an internal/intranet platform
- CORS restricted to `localhost:3000` and `localhost:5173` (prevents cross-origin browser requests from arbitrary origins)
- Production path: integrate Spring Security with JWT bearer tokens; one `@SecurityWebFilterChain` bean; `agentName` scope enforcement per user role

### Input Validation

- All controller request bodies annotated with `@Valid`; `@NotBlank` on required fields
- `@MethodArgumentNotValidException` mapped to HTTP 400 by `GlobalExceptionHandler` — no stack traces exposed
- Generic exception handler returns HTTP 500 with sanitized error message — no internal class names or stack frames leak to clients

### Path Traversal Prevention (`FileReaderTool`)

```java
Path resolved = baseDir.resolve(filename).normalize();
if (!resolved.startsWith(baseDir)) {
    throw new ToolExecutionException("Path traversal detected");
}
```
This is the canonical defense: `normalize()` collapses `../../../etc/passwd` sequences; `startsWith()` enforces the sandbox boundary.

### Domain Allowlist (`HttpClientTool`)

- `agenthub.tools.http-client.allowed-domains`: configurable list; empty = unrestricted (default dev behavior)
- Production deployments should configure an explicit allowlist

### LLM Prompt Injection Defense

- `extractJson()` in `ToolSelectionService` strips all text outside `{...}` boundaries — limits blast radius of injected instructions in LLM response
- Prompt templates use `{{variable}}` substitution, not string concatenation with raw user input — user input is always scoped to `{{user_input}}` variable, isolated from structural instructions

### Threat Model (Basic)

| Threat | Vector | Mitigation |
|---|---|---|
| Path traversal | `FileReaderTool` filename parameter | `normalize().startsWith()` check |
| SSRF | `HttpClientTool` URL parameter | Domain allowlist |
| Prompt injection | User input embedded in agent prompt | Variable-scoped substitution; LLM output parsing hardening |
| Arbitrary code execution | `CodeExecutionTool` | Disabled by default; static validation; subprocess timeout |
| Internal service exposure | Direct client calls to Ollama/ChromaDB | Services on Docker network; not exposed on host (no public port binding in compose) |
| Data exfiltration via LLM | User data sent to hosted LLM | On-premises Ollama — data never leaves infrastructure |

### Docker Security

- Backend runs as non-root user `appuser:appgroup` created in Dockerfile
- No `--privileged` flags; no host network mode
- Ollama port (11434) not bound to host interface (internal Docker network only)

---

## 10. Observability

### Logging Strategy

- **Format:** Logback pattern with MDC: `[requestId] [httpMethod] [uri]` on every log line — correlated end-to-end
- **MDCRequestFilter (`@Order(HIGHEST_PRECEDENCE)`):** extracts `X-Request-ID` header (or generates UUID); injects into MDC; echoes back in response header
- **DAG correlation:** each DAG node thread gets `dagExecutionId` + `dagNodeId` in MDC — distributed trace without a dedicated tracing agent
- **Dev profile:** `com.agenthub: DEBUG` — verbose LLM request/response logging
- **Prod profile:** `com.agenthub: INFO` — no LLM prompt content logged (privacy)

### Metrics (Micrometer → Prometheus)

| Metric Name | Type | Tags | What it Measures |
|---|---|---|---|
| `llm.generate.duration` | Timer | `model`, `status` | End-to-end LLM call latency |
| `llm.generate.errors` | Counter | `model` | LLM error rate |
| `llm.stream.errors` | Counter | `model` | Stream failure rate |
| `dag.execution.duration` | Timer | `node_count` | Full DAG wall-clock time |
| `dag.node.duration` | Timer | `node_id`, `agent`, `status` | Per-node latency breakdown |
| `orchestrator.chain.duration` | Timer | `chain_length` | Chain execution end-to-end |
| `orchestrator.workflow.duration` | Timer | `node_count`, `status` | Workflow (DAG) orchestrator view |
| `orchestrator.workflow.total` | Counter | `status` | Workflow success/failure totals |

Scrape endpoint: `GET /actuator/prometheus`

### Monitoring and Alerting (Recommended)

```
Prometheus → Grafana dashboard:
  - LLM P99 latency  → alert if > 30s
  - LLM error rate   → alert if > 10% over 5min
  - Circuit breaker state (open=1, closed=0) → alert on open
  - DAG timeout rate → alert on spike
  - JVM heap usage   → alert at 85%
```

### Distributed Tracing

- Current: MDC-based correlation (lightweight, zero overhead)
- Future path: OpenTelemetry Java agent — export traces to Jaeger/Zipkin with zero code changes; MDC `requestId` would become the trace ID

---

## 11. Deployment and DevOps

### Docker Compose Stack

```
postgres:16-alpine   — application database
redis:7-alpine       — session memory
chromadb/chroma      — vector knowledge base
ollama/ollama        — local LLM inference (4 GB RAM reserved)
backend              — Spring Boot (depends_on: postgres healthy, redis healthy, ollama started)
frontend             — React/Nginx SPA (proxies /api/* to backend)
```

**Startup Health Checks:**
- PostgreSQL: `pg_isready -U agenthub` (interval 5s, retries 5)
- Redis: `redis-cli ping`
- Backend: `wget -q --spider http://localhost:8080/api/health` (start_period 30s — JVM warmup buffer)

### Backend Dockerfile (Multi-Stage)

```dockerfile
# Stage 1 — Build
FROM eclipse-temurin:21-jdk-alpine AS builder
RUN mvn clean package -DskipTests

# Stage 2 — Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup appgroup && adduser -G appgroup appuser
USER appuser
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "agenthub-backend.jar"]
```

**Why ZGC:** Sub-millisecond GC pauses — critical for streaming responses where a GC stop-the-world would interrupt the SSE `Flux<String>` and cause client-visible gaps.

### Frontend Dockerfile (Multi-Stage)

```dockerfile
# Stage 1 — Build
FROM node:20-alpine AS builder
RUN npm install && npm run build

# Stage 2 — Serve
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

**Nginx:** `try_files $uri $uri/ /index.html` for SPA routing; `proxy_pass http://backend:8080` for `/api/*`; `proxy_read_timeout 120s` to handle long LLM calls.

### Environment Configuration

| Variable | Service | Default |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Backend | localhost, 5432, agenthub, agenthub, agenthub |
| `REDIS_HOST`, `REDIS_PORT` | Backend | localhost, 6379 |
| `OLLAMA_BASE_URL` | Backend | http://localhost:11434 |
| `SPRING_PROFILES_ACTIVE` | Backend | (not set = prod defaults) |

### Local vs. Production Setup

| Concern | Local (Dev) | Production |
|---|---|---|
| Database | H2 in-memory (`create-drop`) | PostgreSQL 16 + Flyway migrations |
| Short-term memory | `ConcurrentHashMap` | Redis 7 (TTL 60 min) |
| Vector store | `InMemoryVectorStore` | ChromaDB |
| LLM | Ollama (local) | Ollama (local, GPU server) |
| Schema management | None (Hibernate auto) | Flyway (versioned migrations) |
| Logging | DEBUG | INFO |
| H2 Console | `/h2-console` enabled | Disabled |

### CI/CD Approach (Recommended, Not Implemented)

```
GitHub Actions:
  on: push to main / PR
  jobs:
    test: mvn test
    build: docker build backend && docker build frontend
    scan: OWASP Dependency-Check
    push: docker push registry
    deploy: docker-compose pull && docker-compose up -d
```

---

## 12. Trade-offs and Design Decisions

### What Was Consciously Traded Off

| Decision | Trade-off Made | Reason |
|---|---|---|
| **Synchronous LLM calls in chain/DAG nodes** | Higher latency per chain step | Simplicity; DAG parallelism mitigates this at the workflow level |
| **In-memory vector store in dev** | Data lost on restart | Acceptable in dev; production uses ChromaDB with persistent storage |
| **Ollama as sole LLM provider** | No fallback if Ollama is down | Resilience4j circuit breaker limits blast radius; future `LLMClient` implementations (OpenAI) are easy to add |
| **No auth layer** | Open API surface | Platform designed as internal service; auth is an infrastructure concern (API gateway / sidecar) |
| **UUIDs (v4) as PKs** | Larger index size vs. auto-increment | No sequential hotspot; globally unique IDs; predictable external-facing format |
| **Fixed backoff (2s) in retry** | No jitter — possible thundering herd | Acceptable with a single Ollama instance; would add `randomized-wait` for multi-instance setups |
| **Template Method for agents** | Coupling to `AbstractAgent` base class | Reduces duplication significantly; dependency injection for all collaborators keeps it testable |
| **Kahn's algorithm for topological sort** | BFS; not DFS | Simpler mental model for cycle detection and in-degree tracking; BFS naturally gives a valid execution order |
| **Sentence-boundary chunking (regex)** | Regex `(?<=[.!?])\s+` misses edge cases (Dr., Mr., ellipsis) | Good enough for structured documents; production alternative: nltk sentence tokenizer or spaCy |

### What Would Be Improved With More Time

- **LLM provider abstraction:** New `LLMClient` implementations for OpenAI, Anthropic, Google Gemini — selectable per agent via configuration, not code
- **Rate limiting:** Per-user rate limiter on agent execution endpoint (Resilience4j `@RateLimiter`)
- **Authentication:** Spring Security + JWT; per-session token scoped to `userId`
- **Distributed tracing:** OpenTelemetry agent → trace propagation from HTTP request through DAG node threads to Ollama call
- **pgvector:** Replace ChromaDB + `InMemoryVectorStore` with a single PostgreSQL `pgvector` extension — eliminates one external dependency
- **Streaming memory write:** Currently memory is written only on stream completion; intermediate checkpointing would allow resumable streams
- **UUID v7:** Time-ordered UUIDs for PKs — preserve insertion-order locality in B-tree indexes
- **Async message persistence:** Current `AgentService` persists messages inline (synchronous JPA); a `@TransactionalEventListener` + async write would reduce P50 latency
- **Health checks per tool:** `HealthController` currently returns a static `UP`; a real health check would probe Ollama, Redis, ChromaDB, and PostgreSQL connectivity

---

## 13. Interview Questions and Answers

### Basic — Project Explanation and Tech Choices

**Q: What is AgentHub in one paragraph?**

> AgentHub is a multi-agent AI orchestration platform built on Spring Boot 3 and Java 17. It exposes a REST + SSE API that routes user requests to specialized AI agents — a task planner, code reviewer, knowledge retrieval agent, prompt optimizer, and an action agent with tool use. Agents share a common execution framework (template method pattern) but each specializes its prompt construction. The system supports four orchestration modes: single-agent, sequential chain, parallel DAG workflow, and real-time streaming. It uses Ollama for on-premises LLM inference, Redis for session memory, ChromaDB for vector-based RAG, and PostgreSQL for persistent session/message storage.

---

**Q: Why did you use Ollama instead of the OpenAI API?**

> Three reasons: cost, data privacy, and latency control. Ollama runs locally, so there are no per-token charges and user data never leaves the infrastructure — critical for enterprise deployments. Latency is more predictable on dedicated hardware vs. a shared API with rate limits and variable network conditions. The trade-off is operational overhead (GPU/RAM requirements, model management). The design mitigates this through the `LLMClient` interface — adding an `OpenAILLMClient` is a drop-in replacement with zero changes to any agent or orchestrator code.

---

**Q: Why Spring Boot over FastAPI or Node.js for this project?**

> The project is heavy on concurrency (DAG parallel execution across multiple `CompletableFuture` threads), strongly typed abstractions (agent interfaces, LLM option records), and production concerns (Resilience4j, Micrometer, Spring Data JPA). Spring Boot's ecosystem makes all of these first-class citizens. Python/FastAPI would have been faster to prototype but harder to scale and type-safely wire. Node.js lacks mature DAG parallel execution primitives. The JVM's threading model maps cleanly to the concurrent DAG executor.

---

**Q: What patterns did you use, and why?**

> - **Template Method** (`AbstractAgent`): eliminates duplication of memory + LLM call logic across 5 agent types while keeping prompt construction customizable per agent.
> - **Strategy** (memory, LLM client, vector store): all swappable at runtime via Spring conditional beans — switching from in-memory to Redis requires only a property change.
> - **Registry** (`AgentRegistry`, `ToolRegistry`): Spring injects a `List<Agent>` and the registry self-populates — no hardcoded mapping, agents are discoverable.
> - **Null Object** (`NoOpLongTermMemory`): prevents null checks throughout the codebase when long-term memory is not configured.
> - **Builder** (`DAG.Builder`, `LLMOptions.Builder`): DAG construction has multi-step validation (cycles, missing deps) that a constructor can't enforce safely; Builder pattern localizes that validation.

---

### Intermediate — Design Decisions and Trade-offs

**Q: How does the DAG executor ensure correctness under concurrent execution?**

> Each DAG node executes inside a `CompletableFuture` that depends on its dependency futures via `CompletableFuture.allOf(depFutures)`. Java's `CompletableFuture` guarantees that `thenApplyAsync` does not fire until all prerequisite futures complete — this is the memory visibility guarantee. All result storage is in a `ConcurrentHashMap<nodeId, Future>` which is safe for concurrent reads and writes. The topological order is computed once before scheduling, so there's no runtime cycle detection needed during execution. The global timeout (`nodeTimeoutSeconds * dag.size()`) prevents any hung future from blocking indefinitely.

---

**Q: What happens if a dependency node fails in a DAG workflow?**

> The failure cascades as `Skipped` rather than `Failed` for downstream nodes. When a node's `CompletableFuture` completes with a `DAGNodeResult.failure()`, all dependents check `depResult.success()` before executing. A `false` result causes the dependent to immediately return `DAGNodeResult.failure("Skipped: dependency 'X' failed")` without invoking the agent at all. The `DAGExecutionResult.success` is only `true` if every node's result is successful. This distinction between `FAILED` and `SKIPPED` is critical for root-cause analysis — the root cause is always the first `FAILED` node; everything else with `SKIPPED` is collateral.

---

**Q: How does the short-term memory work under high concurrency? Could sessions conflict?**

> Each session has a unique `sessionId` (UUID), which is the key for both the Redis List and the `InMemoryShortTermMemory` `ConcurrentHashMap` entry. Sessions are fully isolated — no cross-session key collisions by design. Within a single session, Redis List operations (`RPUSH`/`LTRIM`/`LRANGE`) are atomic at the server level. The in-memory implementation uses a `ConcurrentHashMap` with a synchronized block per session entry on writes to enforce the max-history trim. Two concurrent requests on the same session (rare in a chat UI) could interleave memory writes, but the outcome is at-most two extra entries beyond the limit (trimmed on the next write) — acceptable for a conversation context buffer.

---

**Q: How does the RAG chunking strategy affect retrieval quality?**

> The current sentence-boundary chunker (`(?<=[.!?])\s+` regex with 50-char overlap) is a pragmatic choice that works well for structured text (documentation, code comments, articles). The 50-char overlap reduces the risk of relevant context being split across chunk boundaries. The trade-off: the regex fails on abbreviations (`Dr.`, `e.g.`) and markdown headers. Production improvements would use a proper sentence tokenizer (Apache OpenNLP, spaCy) and experiment with larger chunks (1000 chars) for narrative text vs. smaller chunks (200 chars) for technical specs. Top-K is configurable per request (`topK` parameter) — callers can tune recall vs. noise.

---

**Q: Why use a `ConcurrentHashMap` for the agent registry instead of a database lookup?**

> Agents are a closed, finite set known at startup. A database round-trip per request would add 1–5ms of latency to every single agent call — unnecessary for data that never changes at runtime. The `ConcurrentHashMap` is populated once at Spring context initialization and is effectively immutable thereafter (no writes after startup). This is deliberately a read-heavy, write-once data structure — exactly the use case `ConcurrentHashMap` is optimized for. If agents became dynamic (user-defined at runtime), the registry would need a persistent backing store with a cache invalidation strategy.

---

**Q: How do you handle the case where Ollama is slow or down during a streaming request?**

> The `LLMClient.stream()` method is annotated with `@Retry(name="llmClient")`, so initial connection failures are retried up to 3 times. Once the stream is established and chunks are flowing, a connection interruption surfaces as an error event in the `Flux<String>` — the `onError` handler in `StreamController` closes the SSE connection gracefully. The circuit breaker monitors stream initialization failures; if enough streams fail to connect, it opens and subsequent requests fail fast without even attempting the Ollama connection. The client-side behavior (frontend) should implement a reconnect button rather than auto-retrying SSE, since a partial response was already delivered.

---

### Advanced — Scaling, Failure Scenarios, Optimizations

**Q: How would you scale AgentHub to handle 1000 concurrent users?**

> Bottleneck analysis: each user request blocks for 1–30s waiting for Ollama. With `agentExecutor` at max-16 threads, we hit saturation at ~16 concurrent LLM calls. Solutions:
>
> 1. **Horizontal backend scaling:** Multiple Spring Boot instances behind a load balancer. Session memory is in Redis (external), so any instance can serve any session. Database connections pooled per instance (HikariCP).
> 2. **Ollama scaling:** Multiple Ollama instances with a GPU-aware load balancer. `OLLAMA_BASE_URL` becomes a load-balanced endpoint.
> 3. **Async queue:** Move agent execution off the request thread; accept request, enqueue to a Kafka/RabbitMQ queue, poll for result. Decouples request acceptance throughput from LLM processing throughput.
> 4. **Streaming prioritization:** Streaming requests return tokens immediately, reducing perceived latency at the cost of no caching benefit. Streaming can serve 1000 users on fewer threads than blocking calls.
> 5. **Virtual threads (Java 21):** Remove thread-pool caps; each request gets a virtual thread — blocking I/O on Ollama is "free" from a thread perspective.

---

**Q: What are the memory implications of the in-memory vector store at scale?**

> `InMemoryVectorStore` holds every document chunk as a `StoredEntry` with a `float[768]` embedding (768 floats × 4 bytes = 3 KB per chunk). At 10,000 chunks, that's ~30 MB of embedding data plus the content strings. At 100,000 chunks, ~300 MB — feasible but approaching JVM heap pressure territory. The search is O(n) cosine similarity scan — at 100,000 chunks, that's 400,000 float multiply-adds per query. For production with large knowledge bases, ChromaDB (with HNSW/ANN indexing) drops query complexity from O(n) to O(log n) at the cost of approximate rather than exact search. The swap is zero-code-change — just set `agenthub.memory.chroma.enabled=true`.

---

**Q: How would you detect and handle a misbehaving agent in a long-running DAG?**

> Current mechanisms: global timeout `nodeTimeoutSeconds * dag.size()` catches runaway nodes. Individual node futures don't have per-node timeouts — this is a known gap. Improvements:
>
> 1. **Per-node timeout:** Wrap each `CompletableFuture` with `orTimeout(nodeTimeoutSeconds, SECONDS)` — nodes that exceed their individual SLA are cancelled and marked as failed.
> 2. **LLM circuit breaker:** If the LLM client's circuit opens mid-DAG, remaining nodes that call the LLM also fail fast rather than blocking.
> 3. **Bulkhead:** Resilience4j `@Bulkhead` on `LLMClient.generate()` limits concurrent LLM calls from DAG nodes — prevents one large DAG from starving other requests.
> 4. **Observability:** `dag.node.duration` timer with `node_id` tag exposes which nodes are slow — alert on P99 outliers.

---

**Q: How do you prevent the circuit breaker from being too aggressive and breaking under healthy transient errors?**

> The configuration uses a **count-based sliding window** of 10 calls, 50% threshold. This means at LEAST 5 out of 10 calls must fail before opening. Transient errors (one or two slow responses) won't trigger it. The `wait-duration-in-open-state: 30s` gives Ollama sufficient time to recover from a restart or model swap. Three half-open probe calls confirm recovery before closing — prevents premature close under still-degraded conditions. If false positives become a concern, switch to `TIME_BASED` sliding window (e.g., 30-second window) to reduce sensitivity to burst failures.

---

**Q: How does MDC correlation work across async executor threads?**

> Logback's MDC stores per-thread context. When a request transitions to an `@Async("agentExecutor")` thread, the MDC is NOT automatically copied — it uses a fresh thread context. To fix this, MDC values are explicitly propagated at the point of `CompletableFuture.supplyAsync()` creation: the calling thread's MDC map is captured, then `MDC.setContextMap()` is called at the start of the lambda. In `DAGExecutorService`, each DAG node lambda explicitly calls `MDC.put("dagExecutionId", ...)` and `MDC.put("dagNodeId", ...)` at the start of execution. This ensures log lines in DAG node threads are stamped with both the originating HTTP request ID and the specific DAG node context.

---

### Scenario-Based Questions

**Q: What happens if Redis goes down in production?**

> Short-term memory reads return empty context (no recent conversation history injected into prompts). Short-term memory writes fail silently — the agent response is still returned successfully, but the interaction is not persisted in Redis. Long-term memory (ChromaDB) is unaffected. New sessions can still be created; all agents still function, just without recent conversation context. This degrades gracefully from "contextually aware assistant" to "stateless assistant" — acceptable for most read requests.
>
> Recovery: when Redis restores, new interactions start accumulating in memory again. No data loss on the JPA side (all messages are still persisted to PostgreSQL). A startup health check probing `redis-cli ping` would surface this immediately.

---

**Q: What happens if ChromaDB goes down during a knowledge retrieval request?**

> `SemanticSearchService.isAvailable()` returns `false` when ChromaDB is unreachable. `KnowledgeRetrievalAgent.buildPrompt()` calls `SemanticSearchService.search()`, which returns an empty list when unavailable. The `{{retrieved_context}}` placeholder is rendered as empty string. The agent still calls the LLM and returns a response — just without RAG augmentation. The response will be lower quality (no knowledge base context) but the system does not fail. This is the Null Object pattern in action: `NoOpLongTermMemory` and the `isAvailable()` guard together ensure graceful degradation.

---

**Q: A user reports their conversation history is missing after a server restart. What happened and how do you fix it?**

> Both Redis and in-memory short-term memory are volatile. Redis requires AOF/RDB persistence configured to survive restarts; without it, all keys are lost on restart. In-memory (`InMemoryShortTermMemory`) always loses data on restart — it's intentionally dev-only. The JPA messages table **does** survive restarts (it's committed to PostgreSQL). The fix: on session load (`GET /api/sessions/{id}/history`), check if Redis is empty for a session that has JPA messages — if so, replay the last N JPA messages into Redis to warm up the short-term memory. This "memory replay" on session restore is a planned enhancement.

---

**Q: How would you add a new agent type to AgentHub?**

> 1. Create a class extending `AbstractAgent` — implement `getAgentName()`, `getDescription()`, and `buildPrompt()` only.
> 2. Annotate with `@Component` — Spring auto-injects it into `AgentRegistry` via `List<Agent>` injection.
> 3. Create `src/main/resources/prompts/NewAgent.txt` with `{{variable}}` placeholders.
> 4. No changes to controllers, orchestrator, registry, or any other class.
>
> The registry finds new agents automatically; the DAG executor refers to agents by name string — `"NewAgent"` works immediately. This is the open/closed principle in practice.

---

**Q: If two DAG nodes both need the same tool (e.g., `FileReader`), will they conflict?**

> No. `Tool` implementations are **stateless Spring beans** (singletons). `FileReaderTool.execute(Map<String,Object>)` takes all its parameters from the method argument — no instance-level mutable state. Two concurrent DAG node threads calling `FileReaderTool.execute()` simultaneously share no mutable state, so there's no conflict. The `ToolRegistry` is also read-only after startup (`ConcurrentHashMap`, written only at initialization). The only contention point would be filesystem I/O on the `./data` directory, which is handled by the OS with no application-level locking needed for reads.

---

## 14. Elevator Pitch

### Variation 1 — Backend-Focused (60 seconds)

> "AgentHub is a multi-agent AI backend built on Spring Boot 3. It exposes a REST and Server-Sent Events API that routes requests to specialized AI agents — each with its own prompt template, parameter contract, and behavior. The system supports four orchestration modes: single-agent call, sequential chaining where each agent's output feeds the next, parallel DAG workflows with arbitrary dependency graphs, and real-time token streaming. State is managed in a two-tier memory system: Redis for short-term session context and ChromaDB for long-term RAG knowledge. All LLM calls go through a resilience layer with retry and circuit breaker, and every execution path emits Micrometer metrics for Prometheus. The entire dependency graph — Ollama for inference, PostgreSQL for persistence, Redis, ChromaDB — runs locally in Docker Compose with no external cloud dependencies."

### Variation 2 — System Design-Focused (60 seconds)

> "AgentHub is a system design exercise in building an extensible AI orchestration engine from first principles. The core challenge was: how do you execute arbitrary agent workflows with complex dependency graphs in parallel, while maintaining observability and fault tolerance end-to-end? The answer is a DAG execution engine where each node is a `CompletableFuture` chained through dependency futures — this gives you true parallelism at the platform level without writing concurrency logic inside each agent. Agents are designed as stateless singletons using the Template Method pattern — memory, LLM calls, and error handling live in the base class; prompt construction is the only thing each agent implements. The system is fully profile-driven: dev uses H2 + in-memory stores; prod uses PostgreSQL + Redis + ChromaDB with zero code changes."

### Variation 3 — AI/LLM-Focused (60 seconds)

> "AgentHub demonstrates how to build a production-grade RAG and multi-agent system without relying on LangChain or similar black-box frameworks. LLM inference runs on Ollama locally, so no data leaves your infrastructure. The RAG pipeline uses the `nomic-embed-text` model to embed document chunks into a vector store, then retrieves semantically relevant passages at query time to augment the LLM's context window. The tool use system is LLM-driven: instead of hardcoded tool selection, the system prompts the LLM with a tool catalog and asks it to select the right tool and parameters as structured JSON — making tool dispatch dynamic and extensible without code changes. Prompt templates are stored externally as `.txt` files with `{{variable}}` placeholders, meaning prompt iteration happens without redeployment."

---

## 15. Resume Bullet Mapping

**Strong resume bullet points for AgentHub:**

---

- Architected a **multi-agent AI orchestration platform** in Java/Spring Boot 3 supporting four execution modes (single, chain, parallel DAG, SSE streaming), serving concurrent requests across a configurable thread pool of up to 16 agent executors

- Designed and implemented a **DAG execution engine** using `CompletableFuture` chaining with topological ordering (Kahn's algorithm), enabling up to 8 nodes to execute in parallel with dependency-aware failure propagation and configurable global timeout

- Built an end-to-end **RAG pipeline** from scratch — sentence-boundary text chunking with 50-char overlap, cosine similarity vector search, and embedding via `nomic-embed-text` — integrated with ChromaDB (production) and an in-memory vector store (dev) through a single strategy interface

- Integrated **Resilience4j** retry (3 attempts, 2s backoff) and circuit breaker (50% failure rate, 30s cooldown) on all LLM calls; circuit state changes and per-call latency exposed as **Micrometer timers** scraped by Prometheus

- Implemented a **two-tier memory system**: short-term session context via Redis Lists (FIFO, 60-min TTL, key-isolated per session) and long-term semantic memory via ChromaDB or in-memory cosine store, with profile-based switching between implementations via `@ConditionalOnProperty`

- Engineered a **path-traversal-safe file tool** and **domain-allowlisted HTTP tool**; LLM-driven tool selection via structured JSON prompting with markdown fence stripping and registry-backed hallucination guard

- Developed **MDC-correlated distributed request tracing** — injecting `requestId` at the HTTP filter boundary and propagating `dagExecutionId`/`dagNodeId` per DAG thread — enabling end-to-end log correlation without an external tracing agent

- Delivered full **profile-based environment parity**: dev profile uses H2 + in-memory stores with zero external dependencies; production profile uses PostgreSQL 16 (schema managed by Flyway migrations) + Redis 7 + ChromaDB with identical application code

- Containerized the entire stack with **Docker Compose multi-stage builds** (JRE-only runtime image, non-root user, ZGC for SSE streaming), with health-check-gated startup ordering across 6 services (PostgreSQL, Redis, ChromaDB, Ollama, backend, frontend)

- Achieved **22-class test suite coverage** across all subsystems using JUnit 5 + Mockito; LLM client tested with OkHttp MockWebServer; DAG parallel execution verified with `CountDownLatch` + `AtomicInteger` concurrency assertions

---

*End of document.*
