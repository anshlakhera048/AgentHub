# AgentHub — Postman Testing Guide

Complete guide to setting up Postman and testing every AgentHub API endpoint.

---

## Postman Setup

### 1. Install Postman

Download and install from [https://www.postman.com/downloads](https://www.postman.com/downloads).

### 2. Create a New Collection

1. Open Postman and click **New** → **Collection**
2. Name it **AgentHub API**
3. Go to the **Variables** tab and add:

| Variable | Initial Value | Current Value |
|----------|--------------|---------------|
| `baseUrl` | `http://localhost:8080` | `http://localhost:8080` |

4. Click **Save**

### 3. Set Default Headers

In the **AgentHub API** collection, go to the **Headers** tab (pre-request) and add:

| Key | Value |
|-----|-------|
| `Content-Type` | `application/json` |

All requests below will use `{{baseUrl}}` as the base URL.

---

## Before You Start Testing

Make sure these are running (in this order):

1. **Ollama** — should be running automatically if installed, verify with:
   ```
   GET http://localhost:11434/
   → "Ollama is running"
   ```
2. **Backend** — start with `mvn spring-boot:run -Dspring-boot.run.profiles=dev` from the `backend/` folder
3. The **mistral** and **nomic-embed-text** models must be pulled in Ollama

---

## Endpoints

### 1. Health Check

Verifies the backend is running.

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/health`
- **Body:** None

**Expected Response** (`200 OK`):

```json
{
  "status": "UP",
  "service": "AgentHub",
  "timestamp": "2026-03-30T06:19:07.152Z"
}
```

**What to verify:** `status` is `"UP"`.

---

### 2. List All Agents

Returns all registered AI agents.

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/agents`
- **Body:** None

**Expected Response** (`200 OK`):

```json
[
  {
    "id": null,
    "name": "PromptOptimizer",
    "description": "Analyzes and optimizes user prompts for better LLM responses. Improves clarity, adds constraints, and structures the prompt effectively.",
    "enabled": true
  },
  {
    "id": null,
    "name": "CodeReview",
    "description": "Reviews code for bugs, security issues, performance problems, and best practice violations. Provides actionable improvement suggestions.",
    "enabled": true
  },
  {
    "id": null,
    "name": "KnowledgeRetrieval",
    "description": "Retrieves and synthesizes information from the knowledge base. Uses RAG to find relevant context and generate accurate answers.",
    "enabled": true
  },
  {
    "id": null,
    "name": "ActionAgent",
    "description": "Tool-enabled agent that can read files, make HTTP requests, and execute code to fulfill user requests. Uses LLM-driven tool selection.",
    "enabled": true
  },
  {
    "id": null,
    "name": "TaskPlanner",
    "description": "Breaks down complex tasks into structured, actionable sub-tasks. Creates execution plans with dependencies and priorities.",
    "enabled": true
  }
]
```

**What to verify:** You get 5 agents, all with `enabled: true`.

---

### 3. Execute Agent — PromptOptimizer

Optimizes a prompt for better LLM output.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body** (raw JSON):

```json
{
  "agentName": "PromptOptimizer",
  "input": "Write code for sorting"
}
```

**Expected Response** (`200 OK`):

```json
{
  "requestId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "agentName": "PromptOptimizer",
  "output": "... optimized prompt text ...",
  "latencyMs": 15000,
  "success": true,
  "errorMessage": null,
  "chainResults": [],
  "metadata": {},
  "timestamp": "2026-03-30T06:40:11.514Z"
}
```

**What to verify:** `success` is `true`, `output` contains an improved version of your prompt, `errorMessage` is `null`.

> **Note:** This call takes 10–60 seconds depending on your hardware (Ollama runs the LLM locally).

---

### 4. Execute Agent — TaskPlanner

Breaks a complex task into structured sub-tasks.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body** (raw JSON):

```json
{
  "agentName": "TaskPlanner",
  "input": "Build a REST API for a todo application with user authentication"
}
```

**Expected Response** (`200 OK`):

```json
{
  "requestId": "...",
  "agentName": "TaskPlanner",
  "output": "**Overview:** ... **Phases:** ... Phase 1: Setup ... Phase 2: ...",
  "latencyMs": 25000,
  "success": true,
  "errorMessage": null,
  "chainResults": [],
  "metadata": {},
  "timestamp": "..."
}
```

**What to verify:** `success` is `true`, `output` contains a structured plan with phases and tasks.

---

### 5. Execute Agent — CodeReview

Reviews code for bugs, security issues, and best practices.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body** (raw JSON):

```json
{
  "agentName": "CodeReview",
  "input": "public class UserService {\n  public User getUser(String id) {\n    String query = \"SELECT * FROM users WHERE id = '\" + id + \"'\";\n    return db.execute(query);\n  }\n}"
}
```

**Expected Response** (`200 OK`):

```json
{
  "requestId": "...",
  "agentName": "CodeReview",
  "output": "... SQL injection vulnerability ... use parameterized queries ...",
  "latencyMs": 20000,
  "success": true,
  "errorMessage": null,
  "chainResults": [],
  "metadata": {},
  "timestamp": "..."
}
```

**What to verify:** `success` is `true`, `output` identifies the SQL injection issue and suggests fixes.

---

### 6. Execute Agent — KnowledgeRetrieval

Retrieves answers from the ingested knowledge base. You should ingest documents first (see endpoint #11).

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body** (raw JSON):

```json
{
  "agentName": "KnowledgeRetrieval",
  "input": "What is AgentHub?"
}
```

**Expected Response** (`200 OK`):

```json
{
  "requestId": "...",
  "agentName": "KnowledgeRetrieval",
  "output": "... answer based on ingested knowledge ...",
  "latencyMs": 30000,
  "success": true,
  "errorMessage": null,
  "chainResults": [],
  "metadata": {},
  "timestamp": "..."
}
```

**What to verify:** `success` is `true`. If no documents have been ingested, the output will say it has no relevant context.

---

### 7. Execute Agent — ActionAgent

A tool-using agent that can read files, make HTTP requests, and execute code.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body** (raw JSON):

```json
{
  "agentName": "ActionAgent",
  "input": "Read the file README.md and summarize it"
}
```

**Expected Response** (`200 OK`):

```json
{
  "requestId": "...",
  "agentName": "ActionAgent",
  "output": "... summary of the file or tool execution result ...",
  "latencyMs": 35000,
  "success": true,
  "errorMessage": null,
  "chainResults": [],
  "metadata": {},
  "timestamp": "..."
}
```

**What to verify:** `success` is `true`, `output` contains information about the file or task result.

---

### 8. Execute Agent — With Optional Parameters

All agent execute calls support optional fields.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body** (raw JSON):

```json
{
  "agentName": "TaskPlanner",
  "input": "Create a mobile app",
  "sessionId": null,
  "parameters": {
    "temperature": 0.7,
    "maxTokens": 2000
  },
  "context": {
    "language": "Kotlin",
    "framework": "Jetpack Compose"
  },
  "chainAgents": []
}
```

**Request body fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `agentName` | string | Yes | Name of the agent to execute |
| `input` | string | Yes | The prompt/input text |
| `sessionId` | UUID | No | Link to an existing session for memory |
| `parameters` | object | No | Custom parameters passed to the agent |
| `context` | object | No | Additional context key-value pairs |
| `chainAgents` | array of strings | No | Agents to execute sequentially after the primary |

---

### 9. Agent Chaining

Execute multiple agents in sequence — the output of one becomes the input of the next.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body** (raw JSON):

```json
{
  "agentName": "PromptOptimizer",
  "input": "Write a Python web scraper",
  "chainAgents": ["TaskPlanner", "CodeReview"]
}
```

**What happens:**
1. `PromptOptimizer` optimizes the prompt
2. Its output is fed as input to `TaskPlanner`
3. TaskPlanner's output is fed as input to `CodeReview`

**Expected Response** (`200 OK`):

```json
{
  "requestId": "...",
  "agentName": "PromptOptimizer",
  "output": "... final output from last agent in chain ...",
  "latencyMs": 95000,
  "success": true,
  "errorMessage": null,
  "chainResults": [
    {
      "agentName": "PromptOptimizer",
      "output": "... optimized prompt ...",
      "latencyMs": 15000,
      "success": true,
      "errorMessage": null
    },
    {
      "agentName": "TaskPlanner",
      "output": "... task plan ...",
      "latencyMs": 40000,
      "success": true,
      "errorMessage": null
    },
    {
      "agentName": "CodeReview",
      "output": "... code review ...",
      "latencyMs": 40000,
      "success": true,
      "errorMessage": null
    }
  ],
  "metadata": {},
  "timestamp": "..."
}
```

**What to verify:** `chainResults` contains one entry per agent (including the primary). Each step's `success` is `true`.

---

### 10. Stream Agent Output (Server-Sent Events)

Streams agent output token-by-token as Server-Sent Events — useful for real-time UIs.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/stream`
- **Body** (raw JSON):

```json
{
  "agentName": "TaskPlanner",
  "input": "Design a microservices architecture for an e-commerce platform"
}
```

**Request body fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `agentName` | string | Yes | Name of the agent |
| `input` | string | Yes | The prompt text |
| `sessionId` | UUID | No | Link to a session |
| `parameters` | object | No | Custom parameters |
| `context` | object | No | Additional context |

> **Postman Setup for SSE:**
> Postman does not natively render SSE well. To test this:
> 1. Send the request normally — you will see the raw SSE text chunks in the response body
> 2. Alternatively, test from the browser console:
>    ```js
>    const response = await fetch('http://localhost:8080/api/agents/stream', {
>      method: 'POST',
>      headers: { 'Content-Type': 'application/json' },
>      body: JSON.stringify({ agentName: 'TaskPlanner', input: 'Design a REST API' })
>    });
>    const reader = response.body.getReader();
>    const decoder = new TextDecoder();
>    while (true) {
>      const { done, value } = await reader.read();
>      if (done) break;
>      console.log(decoder.decode(value));
>    }
>    ```
> 3. Or use `curl`:
>    ```bash
>    curl -N -X POST http://localhost:8080/api/agents/stream \
>      -H "Content-Type: application/json" \
>      -d '{"agentName":"TaskPlanner","input":"Design a REST API"}'
>    ```

**Expected Response:** A stream of text tokens that form the full agent output when concatenated. Content-Type is `text/event-stream`.

---

### 11. Ingest Knowledge Document

Ingests text into the vector store so the KnowledgeRetrieval agent can search it later.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/knowledge/ingest`
- **Body** (raw JSON):

```json
{
  "content": "AgentHub is a multi-agent AI orchestration platform. It supports five agents: PromptOptimizer, TaskPlanner, CodeReview, KnowledgeRetrieval, and ActionAgent. Agents can be chained together so the output of one feeds into the next. It also supports DAG-based workflows where agents run in parallel with dependency graphs.",
  "documentId": "agenthub-overview"
}
```

**Request body fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `content` | string | Yes | The text content to ingest |
| `documentId` | string | No | Custom document ID (auto-generated UUID if omitted) |

**Expected Response** (`200 OK`):

```json
{
  "documentId": "agenthub-overview",
  "chunksStored": 1,
  "status": "success"
}
```

**What to verify:** `status` is `"success"`, `chunksStored` is 1 or more (large documents get split into chunks).

**Follow-up test:** After ingesting, go back to endpoint #6 (KnowledgeRetrieval agent) and ask "What is AgentHub?" — the agent should now answer using the ingested content.

---

### 12. Create a Session

Creates a chat session that tracks conversation history across multiple agent calls.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/sessions`
- **Body** (raw JSON):

```json
{
  "agentName": "TaskPlanner",
  "userId": "user-1"
}
```

**Request body fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `agentName` | string | Yes | Agent associated with this session |
| `userId` | string | No | User identifier (optional) |

**Expected Response** (`200 OK`):

```json
{
  "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "userId": "user-1",
  "agentName": "TaskPlanner",
  "createdAt": "2026-03-30T06:30:00Z"
}
```

**What to verify:** Response contains a `id` (UUID). **Save this `id`** — you'll need it for the next endpoints.

---

### 13. Execute Agent with Session (Conversation Memory)

Use the session ID from endpoint #12 to maintain conversation context.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body** (raw JSON):

```json
{
  "agentName": "TaskPlanner",
  "input": "Build a REST API for a blog platform",
  "sessionId": "<paste-session-id-from-step-12>"
}
```

Send multiple requests with the same `sessionId` — the agent will remember previous messages.

**What to verify:** Responses become contextually aware of previous interactions in the session.

---

### 14. Get Session History

Retrieves all messages in a session.

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/sessions/<session-id>/history`

Replace `<session-id>` with the UUID from endpoint #12.

Example: `{{baseUrl}}/api/sessions/a1b2c3d4-e5f6-7890-abcd-ef1234567890/history`

- **Body:** None

**Expected Response** (`200 OK`):

```json
[
  {
    "id": "...",
    "role": "user",
    "content": "Build a REST API for a blog platform",
    "agentName": "TaskPlanner",
    "latencyMs": null,
    "timestamp": "2026-03-30T06:31:00Z"
  },
  {
    "id": "...",
    "role": "assistant",
    "content": "**Overview:** ... structured task plan ...",
    "agentName": "TaskPlanner",
    "latencyMs": 25000,
    "timestamp": "2026-03-30T06:31:25Z"
  }
]
```

**What to verify:** Messages are returned in order. User messages have `role: "user"`, agent responses have `role: "assistant"` with `latencyMs` populated.

---

### 15. List Sessions for a User

Gets all sessions belonging to a user.

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/sessions?userId=user-1`

- **Body:** None

**Query parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `userId` | string | `"default"` | The user ID to filter by |

**Expected Response** (`200 OK`):

```json
[
  {
    "id": "a1b2c3d4-...",
    "userId": "user-1",
    "agentName": "TaskPlanner",
    "createdAt": "2026-03-30T06:30:00Z"
  }
]
```

**What to verify:** Returns all sessions you created with `userId: "user-1"`.

---

### 16. Execute DAG Workflow

Executes multiple agents in a directed acyclic graph (DAG). Agents with no dependencies run in parallel; agents with dependencies wait for their predecessors.

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/workflow/execute`
- **Body** (raw JSON):

```json
{
  "input": "Build a REST API for a todo application",
  "nodes": [
    {
      "id": "plan",
      "agentName": "TaskPlanner",
      "dependencies": [],
      "parameters": {}
    },
    {
      "id": "optimize",
      "agentName": "PromptOptimizer",
      "dependencies": [],
      "parameters": {}
    },
    {
      "id": "review",
      "agentName": "CodeReview",
      "dependencies": ["plan", "optimize"],
      "parameters": {}
    }
  ]
}
```

**Request body fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `input` | string | Yes | Shared input for all nodes |
| `nodes` | array | Yes | At least one node definition |
| `sessionId` | UUID | No | Link to a session |
| `context` | object | No | Additional context |

**Node fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | string | Yes | Unique node identifier |
| `agentName` | string | Yes | Agent to run at this node |
| `dependencies` | array of strings | No | Node IDs that must complete first (defaults to `[]`) |
| `parameters` | object | No | Node-specific parameters (defaults to `{}`) |

**What happens in the example above:**
1. `plan` (TaskPlanner) and `optimize` (PromptOptimizer) run **in parallel** (no dependencies)
2. `review` (CodeReview) waits for both to finish, then runs

**Expected Response** (`200 OK`):

```json
{
  "executionId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "success": true,
  "totalLatencyMs": 60000,
  "nodeResults": {
    "plan": {
      "nodeId": "plan",
      "agentName": "TaskPlanner",
      "output": "... task plan ...",
      "latencyMs": 25000,
      "success": true,
      "errorMessage": null
    },
    "optimize": {
      "nodeId": "optimize",
      "agentName": "PromptOptimizer",
      "output": "... optimized prompt ...",
      "latencyMs": 15000,
      "success": true,
      "errorMessage": null
    },
    "review": {
      "nodeId": "review",
      "agentName": "CodeReview",
      "output": "... code review ...",
      "latencyMs": 20000,
      "success": true,
      "errorMessage": null
    }
  },
  "errorMessage": null,
  "timestamp": "..."
}
```

**What to verify:** `success` is `true`, all entries in `nodeResults` have `success: true`, `totalLatencyMs` should be less than the sum of all individual latencies (because `plan` and `optimize` ran in parallel).

---

## Error Responses

All errors follow this format:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Agent not found: NonExistentAgent",
  "timestamp": "2026-03-30T06:45:00Z"
}
```

### Test Error Cases

#### Invalid Agent Name

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body:**

```json
{
  "agentName": "NonExistentAgent",
  "input": "Hello"
}
```

**Expected:** `404 Not Found` with message `"Agent not found: NonExistentAgent"`.

---

#### Missing Required Fields

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/agents/execute`
- **Body:**

```json
{
  "agentName": "",
  "input": ""
}
```

**Expected:** `400 Bad Request` with validation error messages.

---

#### Empty Workflow Nodes

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/workflow/execute`
- **Body:**

```json
{
  "input": "test",
  "nodes": []
}
```

**Expected:** `400 Bad Request` — nodes must not be empty.

---

#### Invalid Session ID

- **Method:** `GET`
- **URL:** `{{baseUrl}}/api/sessions/00000000-0000-0000-0000-000000000000/history`

**Expected:** `200 OK` with an empty array `[]` (no messages found).

---

## Recommended Testing Flow

Follow this order for a complete end-to-end test:

| Step | Endpoint | Purpose |
|------|----------|---------|
| 1 | `GET /api/health` | Verify backend is up |
| 2 | `GET /api/agents` | Verify agents are registered |
| 3 | `POST /api/agents/execute` (PromptOptimizer) | Test basic agent execution |
| 4 | `POST /api/agents/execute` (TaskPlanner) | Test planning agent |
| 5 | `POST /api/agents/execute` (CodeReview) | Test code review agent |
| 6 | `POST /api/knowledge/ingest` | Ingest a document |
| 7 | `POST /api/agents/execute` (KnowledgeRetrieval) | Test RAG retrieval |
| 8 | `POST /api/agents/execute` with `chainAgents` | Test agent chaining |
| 9 | `POST /api/sessions` | Create a session |
| 10 | `POST /api/agents/execute` with `sessionId` | Test session memory |
| 11 | `GET /api/sessions/{id}/history` | Verify message history |
| 12 | `GET /api/sessions?userId=...` | Verify session listing |
| 13 | `POST /api/workflow/execute` | Test DAG workflow |
| 14 | `POST /api/agents/stream` | Test streaming output |
| 15 | Test error cases | Verify proper error handling |

---

## Tips

- **Timeouts:** Agent calls can take 10–120 seconds depending on prompt complexity and hardware. Set Postman's request timeout to at least **120 seconds** (Settings → General → Request timeout).
- **Streaming:** Postman doesn't render SSE elegantly. Use `curl -N` or the browser console for a better streaming experience.
- **Sessions:** Use sessions when you want the agent to remember context across multiple requests.
- **Chaining vs Workflows:** Use `chainAgents` for simple sequential pipelines. Use `/api/workflow/execute` when you need parallel execution with dependency graphs.
- **The dev profile** uses in-memory storage — all data is lost when the backend restarts.
