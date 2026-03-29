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

## Quick Start

### Prerequisites
- Docker & Docker Compose
- (Optional) Java 21 + Maven for local dev
- (Optional) Node.js 20+ for frontend dev

### Run with Docker Compose

```bash
# Start all services
docker compose up -d

# Pull Ollama model (first time only)
docker exec agenthub-ollama ollama pull mistral

# Access the app
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### Local Development

```bash
# Backend
cd backend
mvn spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

## API Endpoints

| Method | Endpoint                      | Description            |
|--------|-------------------------------|------------------------|
| GET    | `/api/agents`                 | List all agents        |
| POST   | `/api/agents/execute`         | Execute an agent       |
| POST   | `/api/sessions`               | Create a session       |
| GET    | `/api/sessions/{id}/history`  | Get session history    |
| GET    | `/api/sessions?userId=...`    | List user sessions     |
| GET    | `/api/health`                 | Health check           |

## Agents

| Agent               | Purpose                                  |
|---------------------|------------------------------------------|
| PromptOptimizer     | Optimizes prompts for better LLM output  |
| TaskPlanner         | Breaks tasks into structured plans       |
| CodeReview          | Reviews code for bugs and best practices |
| KnowledgeRetrieval  | RAG-based knowledge retrieval            |

## Agent Chaining

Execute multiple agents sequentially — output of one feeds into the next:

```json
{
  "agentName": "PromptOptimizer",
  "input": "Write a sorting algorithm",
  "chainAgents": ["TaskPlanner", "CodeReview"]
}
```

## Project Structure

```
backend/
  src/main/java/com/agenthub/
    agent/          # Agent interface + implementations
    orchestrator/   # Request routing + chaining
    llm/            # LLM client abstraction (Ollama)
    memory/         # Short-term (Redis) + Long-term (ChromaDB)
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
```
