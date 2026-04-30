# AgentHub — Multi-Agent AI Orchestration Platform

A production-grade, modular multi-agent AI orchestration platform that enables intelligent task routing, agent chaining, DAG-based workflows, and RAG-powered knowledge retrieval — all powered by local LLMs via Ollama.

## Project Overview

AgentHub solves the problem of orchestrating multiple specialized AI agents to collaboratively handle complex user requests. Rather than relying on a single monolithic LLM prompt, AgentHub routes tasks to purpose-built agents (code review, task planning, prompt optimization, knowledge retrieval, tool execution) and chains their outputs together — enabling modular, reusable, and observable AI workflows.

The platform provides a full-stack solution with a React frontend for interactive chat, a Spring Boot backend for orchestration, and supports both simple single-agent execution and advanced parallel DAG workflows.

---

## Architecture Overview

```
┌─────────────┐     ┌──────────────────────────────────────────┐
│  React UI   │────▶│           Spring Boot Backend             │
│  (port 3000)│     │                                          │
└─────────────┘     │  Controller → Service → Orchestrator     │
                    │       │            │          │           │
                    │  ┌────▼───┐  ┌─────▼────┐  ┌─▼────────┐ │
                    │  │ Agents │  │ Prompt    │  │  Tool     │ │
                    │  │Registry│  │ Engine    │  │ Registry  │ │
                    │  └────┬───┘  └──────────┘  └──────────┘ │
                    │       │                                   │
                    │  ┌────▼────────────────────────────────┐ │
                    │  │         LLM Gateway (Ollama)        │ │
                    │  └────────────────────────────────────┘ │
                    │  ┌─────────┐  ┌──────────┐  ┌────────┐ │
                    │  │ Redis   │  │ ChromaDB │  │Postgres│ │
                    │  │(memory) │  │(vectors) │  │ (data) │ │
                    │  └─────────┘  └──────────┘  └────────┘ │
                    └──────────────────────────────────────────┘
```

**High-level flow:**

1. User sends a request via React UI or API
2. Controller validates and routes to AgentService
3. Orchestrator selects the target agent (or builds a chain/DAG)
4. Agent retrieves memory context (short-term + long-term)
5. Prompt engine renders a templated prompt with context
6. LLM client calls Ollama for inference
7. Response is persisted and returned to the user

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Backend** | Java 17, Spring Boot 3.2 | REST API, orchestration, DI |
| **Frontend** | React 18, Vite 5 | Interactive chat UI |
| **LLM** | Ollama (Mistral) | Local LLM inference |
| **Database** | PostgreSQL 16 | Session & message persistence |
| **Cache** | Redis 7 | Short-term conversation memory |
| **Vector DB** | ChromaDB | Long-term RAG knowledge store |
| **Embeddings** | Ollama (nomic-embed-text) | Embedding generation for RAG |
| **Observability** | Micrometer + Prometheus | Metrics & monitoring |
| **Resilience** | Resilience4j | Retry, circuit breaker, timeout |
| **API Docs** | SpringDoc OpenAPI 2.3 | Swagger UI |
| **DB Migrations** | Flyway | Schema versioning |
| **Containerization** | Docker Compose | Full-stack orchestration |

---

## Features

- **5 Specialized AI Agents**: PromptOptimizer, TaskPlanner, CodeReview, KnowledgeRetrieval, ActionAgent
- **Agent Chaining**: Sequential multi-agent pipelines with automatic output forwarding
- **DAG Workflows**: Parallel/sequential execution with dependency graphs
- **SSE Streaming**: Real-time token streaming from LLM to frontend
- **RAG Pipeline**: Document ingestion → chunking → embedding → semantic search
- **Dual Memory Architecture**: Short-term (Redis/in-memory) + long-term (vector store)
- **LLM-Driven Tool Selection**: Agents autonomously select and execute tools
- **Session Management**: Persistent chat sessions with full history
- **Structured Observability**: Request tracing via MDC, Prometheus metrics
- **Resilience**: Retry with exponential backoff, circuit breakers, timeouts
- **Dev Mode**: Zero-dependency local development (H2 + in-memory stores)
- **Production Mode**: Full PostgreSQL + Redis + ChromaDB stack via Docker Compose

---

## Prerequisites

| Tool | Version | Required For |
|------|---------|-------------|
| **Java** | 17+ | Backend |
| **Maven** | 3.8+ | Backend build |
| **Node.js** | 18+ | Frontend |
| **Ollama** | latest | LLM inference (required) |
| **Docker & Docker Compose** | latest | Full-stack mode (optional) |

---

## Setup Instructions

### Option 1 — Local Dev Mode (Recommended for Development)

Uses H2 in-memory database and in-memory vector store. Only requires **Ollama** running locally.

#### Step 1: Install and Start Ollama

Download from [https://ollama.com](https://ollama.com) and install. Then pull models:

```bash
ollama pull mistral
ollama pull nomic-embed-text
```

Verify Ollama is running:

```bash
curl http://localhost:11434/
# Expected: Ollama is running
```

#### Step 2: Start the Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

> The `dev` profile uses H2 + in-memory stores. Without it, the app requires PostgreSQL and Redis.

Verify:

```bash
curl http://localhost:8080/api/health
# Returns: {"status":"UP","service":"AgentHub","timestamp":"..."}
```

Endpoints available:
- **API:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **H2 Console:** http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:agenthub`, user: `sa`, no password)

#### Step 3: Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend available at http://localhost:3000 (Vite proxies `/api/*` to backend automatically).

#### Startup Order

```
1. Ollama          ← must be running (LLM engine)
2. Backend         ← connects to Ollama on startup
3. Frontend        ← proxies API calls to backend
```

---

### Option 2 — Docker Compose (Full Production Stack)

Starts all services: PostgreSQL, Redis, ChromaDB, Ollama, Backend, Frontend.

```bash
docker compose up -d

# Pull models (first time only)
docker exec agenthub-ollama ollama pull mistral
docker exec agenthub-ollama ollama pull nomic-embed-text
```

Access:
- **Frontend:** http://localhost:3000
- **Backend API:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html

Stop:

```bash
docker compose down       # Stop services
docker compose down -v    # Stop and remove all data
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | agenthub | Database name |
| `DB_USER` | agenthub | Database username |
| `DB_PASSWORD` | agenthub | Database password |
| `REDIS_HOST` | localhost | Redis host |
| `REDIS_PORT` | 6379 | Redis port |
| `OLLAMA_BASE_URL` | http://localhost:11434 | Ollama API endpoint |
| `OLLAMA_MODEL` | mistral | Default LLM model |
| `CHROMA_BASE_URL` | http://localhost:8000 | ChromaDB endpoint |
| `MEMORY_REDIS_ENABLED` | true | Enable Redis memory |
| `MEMORY_CHROMA_ENABLED` | true | Enable ChromaDB |
| `MEMORY_VECTOR_ENABLED` | false | Enable in-memory vector store |
| `STREAMING_ENABLED` | true | Enable SSE streaming |
| `EXECUTION_THREAD_POOL` | 8 | DAG executor thread pool |
| `EXECUTION_NODE_TIMEOUT` | 120 | Node timeout (seconds) |

---

## Dev Profile vs Default Profile

| Feature | Dev Profile | Default Profile |
|---------|-------------|-----------------|
| Database | H2 in-memory | PostgreSQL |
| Short-term memory | In-memory HashMap | Redis |
| Long-term memory | In-memory vector store | ChromaDB |
| Flyway migrations | Disabled | Enabled |
| External services | Ollama only | All services |
| H2 Console | Enabled | Disabled |

---

## API Endpoints

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Service health check |

### Agents

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/agents` | List all registered agents |
| POST | `/api/agents/execute` | Execute an agent |
| POST | `/api/agents/stream` | Stream agent output (SSE) |

#### POST `/api/agents/execute`

**Request:**
```json
{
  "agentName": "TaskPlanner",
  "input": "Build a REST API for user management",
  "sessionId": "uuid-here",
  "chainAgents": ["CodeReview"],
  "parameters": { "complexity": "high" }
}
```

**Response:**
```json
{
  "requestId": "uuid",
  "agentName": "TaskPlanner",
  "output": "1. Define user entity...",
  "latencyMs": 2340,
  "success": true,
  "chainResults": [],
  "metadata": {},
  "timestamp": "2026-04-30T12:00:00Z"
}
```

### Sessions

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions` | Create a new session |
| GET | `/api/sessions/{id}/history` | Get message history |
| GET | `/api/sessions?userId=default` | List user sessions |
| DELETE | `/api/sessions/{id}` | Delete a session |

### Knowledge

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/knowledge/ingest` | Ingest document into RAG pipeline |

### Workflows

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/workflow/execute` | Execute a DAG workflow |

#### POST `/api/workflow/execute`

```json
{
  "input": "Build a REST API for a todo app",
  "nodes": [
    { "id": "plan", "agentName": "TaskPlanner", "dependencies": [] },
    { "id": "optimize", "agentName": "PromptOptimizer", "dependencies": [] },
    { "id": "review", "agentName": "CodeReview", "dependencies": ["plan", "optimize"] }
  ]
}
```

> For detailed Postman testing instructions, see [docs/postman-testing-guide.md](docs/postman-testing-guide.md).

---

## Agents

| Agent | Purpose | Key Capability |
|-------|---------|----------------|
| **PromptOptimizer** | Optimizes prompts for better LLM output | Prompt engineering |
| **TaskPlanner** | Breaks tasks into structured action plans | Task decomposition |
| **CodeReview** | Reviews code for bugs, security, best practices | Static analysis via LLM |
| **KnowledgeRetrieval** | RAG-based knowledge retrieval & synthesis | Semantic search |
| **ActionAgent** | Tool-enabled agent for file I/O, HTTP, code execution | Tool orchestration |

### Agent Chaining

Execute multiple agents sequentially — output of one feeds into the next:

```json
{
  "agentName": "PromptOptimizer",
  "input": "Write a sorting algorithm",
  "chainAgents": ["TaskPlanner", "CodeReview"]
}
```

---

## Folder Structure

```
AgentHub/
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── data/                          # FileReader tool sandbox
│   └── src/main/java/com/agenthub/
│       ├── agent/                     # Agent interface + 5 implementations
│       ├── orchestrator/              # Request routing, chaining, DAG execution
│       │   └── dag/                   # DAG builder, executor, node models
│       ├── llm/                       # LLM client abstraction (Ollama WebClient)
│       ├── memory/                    # Dual memory architecture
│       │   ├── vector/                # Vector store, ingestion, semantic search
│       │   └── embedding/             # Embedding service (Ollama)
│       ├── prompt/                    # Template engine + loader
│       ├── tools/                     # Tool framework + implementations
│       │   └── impl/                  # FileReader, HttpClient, CodeExecution
│       ├── controller/                # REST controllers
│       ├── service/                   # AgentService (business logic)
│       ├── config/                    # Spring config (CORS, Redis, Async, Resilience)
│       ├── model/
│       │   ├── dto/                   # Request/response records
│       │   └── entity/                # JPA entities
│       └── repository/                # Spring Data JPA repositories
├── frontend/
│   ├── Dockerfile
│   ├── nginx.conf                     # Production reverse proxy config
│   ├── vite.config.js                 # Dev server with API proxy
│   └── src/
│       ├── App.jsx                    # Main app with state management
│       ├── components/                # ChatInterface, AgentSelector, SessionPanel
│       └── services/api.js            # API client
├── docs/
│   └── postman-testing-guide.md       # API testing guide
└── docker-compose.yml                 # Full-stack orchestration
```

---

## Troubleshooting

### Backend fails to start with "Connection refused"

**Cause:** Running without `dev` profile and PostgreSQL/Redis are not available.

**Fix:** Use the dev profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### "Ollama is not reachable" / LLM errors

**Cause:** Ollama is not running or the model hasn't been pulled.

**Fix:**
```bash
ollama serve                  # Start Ollama if not running
ollama pull mistral           # Pull the default model
ollama pull nomic-embed-text  # Pull embedding model
```

### Frontend shows "Failed to load agents"

**Cause:** Backend is not running or CORS issue.

**Fix:** Ensure the backend is running on port 8080 before starting the frontend. The Vite dev server proxies `/api/*` to `localhost:8080`.

### Docker Compose: Backend keeps restarting

**Cause:** Backend starts before Ollama has finished loading models.

**Fix:** Wait for Ollama to fully start, then pull models:
```bash
docker exec agenthub-ollama ollama pull mistral
```

### H2 Console not accessible

**Cause:** Running in default profile (H2 console is only enabled in dev profile).

**Fix:** Access at http://localhost:8080/h2-console with JDBC URL `jdbc:h2:mem:agenthub`, username `sa`, empty password. Only available with `dev` profile.

### Tests fail

All 121 unit tests should pass without any external services:
```bash
cd backend
mvn clean test
```

---

## Future Improvements

- **Agent marketplace**: Dynamic agent registration via API with custom prompt templates
- **WebSocket streaming**: Replace SSE with WebSocket for bidirectional communication
- **Multi-model routing**: Route different agents to different LLM models based on task complexity
- **Persistent vector store**: Replace in-memory vector store with Qdrant or Weaviate for production RAG
- **Authentication**: Add JWT-based auth with user-scoped sessions
- **Rate limiting**: Per-user rate limiting on agent execution endpoints
- **Agent evaluation**: Built-in benchmarking to measure agent quality over time
- **Conversation branching**: Fork sessions to explore alternative agent responses
- **Plugin system**: Hot-loadable tools via classpath scanning or REST registration
- **Observability dashboard**: Grafana dashboards for LLM latency, token usage, and error rates
