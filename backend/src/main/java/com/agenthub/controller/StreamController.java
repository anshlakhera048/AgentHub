package com.agenthub.controller;

import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.StreamRequest;
import com.agenthub.orchestrator.AgentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * SSE streaming endpoint for real-time agent responses.
 */
@RestController
@RequestMapping("/api/agents")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    private final AgentOrchestrator orchestrator;

    public StreamController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamAgent(@RequestBody StreamRequest request) {
        log.info("POST /api/agents/stream - agent: {}", request.agentName());

        AgentRequest agentRequest = new AgentRequest(
                request.agentName(),
                request.input(),
                request.sessionId(),
                request.parameters(),
                null,
                request.context()
        );

        return orchestrator.executeStream(agentRequest);
    }
}
