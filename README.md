# AgentHub — Multi-Agent AI Orchestration Platform

A production-grade, modular multi-agent AI orchestration platform powered by local LLMs via Ollama.

## Architecture

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

## Getting Started

There are two ways to run AgentHub: **Local Dev Mode** (minimal, recommended for development) and **Docker Compose** (full production stack).

### Option 1 — Local Dev Mode (Recommended)

This mode uses an **H2 in-memory database** and **in-memory vector store**, so you only need **Ollama** running. No PostgreSQL, Redis, or ChromaDB required.

#### Step 1: Install and Start Ollama

Download Ollama from [https://ollama.com](https://ollama.com) and install it. Then pull the required models:

```bash
# Pull the LLM model (used by all agents)
ollama pull mistral

# Pull the embedding model (used by KnowledgeRetrieval agent)
ollama pull nomic-embed-text
```

Ollama runs automatically after installation. Verify it's running:

```bash
curl http://localhost:11434/
# Should print: Ollama is running
```

#### Step 2: Start the Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

> **Important:** The `-Dspring-boot.run.profiles=dev` flag is required for local development. Without it, the app tries to connect to PostgreSQL and Redis, which will fail unless you have them running.

Verify the backend is up:

```bash
curl http://localhost:8080/api/health
# Should return: {"status":"UP","service":"AgentHub",...}
```

Available at:
- **Backend API:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **H2 Console:** http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:agenthub`, user: `sa`, no password)

#### Step 3: Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

Available at:
- **Frontend UI:** http://localhost:3000

> The Vite dev server proxies all `/api/*` requests to `http://localhost:8080` automatically.

#### Startup Order Summary

```
1. Ollama          ← must be running first (LLM engine)
2. Backend         ← connects to Ollama on startup
3. Frontend        ← proxies API calls to backend
```

---

### Option 2 — Docker Compose (Full Stack)

This starts **everything** (PostgreSQL, Redis, ChromaDB, Ollama, Backend, Frontend) in containers.

```bash
# Start all services
docker compose up -d

# Pull the Ollama model (first time only)
docker exec agenthub-ollama ollama pull mistral
docker exec agenthub-ollama ollama pull nomic-embed-text

# Access the app
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

To stop everything:

```bash
docker compose down
```

To stop and remove all data (volumes):

```bash
docker compose down -v
```

---

## Dev Profile vs Default Profile

| Feature | Dev Profile (`-Dspring-boot.run.profiles=dev`) | Default Profile |
|---------|-----------------------------------------------|-----------------|
| Database | H2 in-memory | PostgreSQL |
| Short-term memory | In-memory | Redis |
| Long-term memory | In-memory vector store | ChromaDB |
| DB migrations (Flyway) | Disabled | Enabled |
| External services needed | Ollama only | Postgres, Redis, ChromaDB, Ollama |
| H2 Console | Enabled at `/h2-console` | Disabled |

---

## API Endpoints

| Method | Endpoint                      | Description                       |
|--------|-------------------------------|-----------------------------------|
| GET    | `/api/health`                 | Health check                      |
| GET    | `/api/agents`                 | List all registered agents        |
| POST   | `/api/agents/execute`         | Execute an agent (with optional chaining) |
| POST   | `/api/agents/stream`          | Stream agent output (SSE)         |
| POST   | `/api/sessions`               | Create a chat session             |
| GET    | `/api/sessions/{id}/history`  | Get session message history       |
| GET    | `/api/sessions?userId=...`    | List sessions for a user          |
| POST   | `/api/knowledge/ingest`       | Ingest a document into the knowledge base |
| POST   | `/api/workflow/execute`       | Execute a DAG-based multi-agent workflow  |

> For detailed request/response schemas and Postman testing instructions, see [docs/postman-testing-guide.md](docs/postman-testing-guide.md).

## Agents

| Agent               | Purpose                                  |
|---------------------|------------------------------------------|
| PromptOptimizer     | Optimizes prompts for better LLM output  |
| TaskPlanner         | Breaks tasks into structured plans       |
| CodeReview          | Reviews code for bugs and best practices |
| KnowledgeRetrieval  | RAG-based knowledge retrieval            |
| ActionAgent         | Tool-enabled agent (file reader, HTTP client, code execution) |

## Agent Chaining

Execute multiple agents sequentially — output of one feeds into the next:

```json
{
  "agentName": "PromptOptimizer",
  "input": "Write a sorting algorithm",
  "chainAgents": ["TaskPlanner", "CodeReview"]
}
```

## DAG Workflows

Execute multiple agents in parallel/sequential order with dependency graphs:

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

## Project Structure

```
backend/
  src/main/java/com/agenthub/
    agent/          # Agent interface + implementations
    orchestrator/   # Request routing + chaining + DAG execution
    llm/            # LLM client abstraction (Ollama)
    memory/         # Short-term (Redis/In-memory) + Long-term (ChromaDB/Vector)
    prompt/         # Prompt template engine
    tools/          # Tool framework + implementations
    controller/     # REST API controllers
    service/        # Business logic
    config/         # Spring configuration
    model/          # Entities + DTOs
    repository/     # JPA repositories

frontend/
  src/
    components/     # React UI components
    services/       # API client

docs/
  postman-testing-guide.md   # Postman setup & testing guide
```
