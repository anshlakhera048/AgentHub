package com.agenthub.controller;

import com.agenthub.model.dto.*;
import com.agenthub.service.AgentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/execute")
    public ResponseEntity<AgentResponse> execute(@Valid @RequestBody AgentRequest request) {
        log.info("POST /api/agents/execute - agent: {}, sessionId: {}",
                request.agentName(), request.sessionId());
        AgentResponse response = agentService.executeAgent(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AgentInfo>> listAgents() {
        log.info("GET /api/agents");
        return ResponseEntity.ok(agentService.listAgents());
    }
}
