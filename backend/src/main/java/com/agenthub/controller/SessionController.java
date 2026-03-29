package com.agenthub.controller;

import com.agenthub.model.dto.*;
import com.agenthub.service.AgentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final AgentService agentService;

    public SessionController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody SessionCreateRequest request) {
        log.info("POST /api/sessions - user: {}, agent: {}", request.userId(), request.agentName());
        return ResponseEntity.ok(agentService.createSession(request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<MessageResponse>> getHistory(@PathVariable UUID id) {
        log.info("GET /api/sessions/{}/history", id);
        return ResponseEntity.ok(agentService.getSessionHistory(id));
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getUserSessions(
            @RequestParam(defaultValue = "default") String userId) {
        log.info("GET /api/sessions?userId={}", userId);
        return ResponseEntity.ok(agentService.getUserSessions(userId));
    }
}
