package com.agenthub.service;

import com.agenthub.agent.AgentRegistry;
import com.agenthub.model.dto.*;
import com.agenthub.model.entity.MessageEntity;
import com.agenthub.model.entity.SessionEntity;
import com.agenthub.orchestrator.AgentOrchestrator;
import com.agenthub.repository.MessageRepository;
import com.agenthub.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    public AgentService(AgentOrchestrator orchestrator, AgentRegistry agentRegistry,
                        SessionRepository sessionRepository, MessageRepository messageRepository) {
        this.orchestrator = orchestrator;
        this.agentRegistry = agentRegistry;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public AgentResponse executeAgent(AgentRequest request) {
        log.info("Executing agent '{}' for session '{}'", request.agentName(), request.sessionId());

        // Persist user message
        if (request.sessionId() != null) {
            persistMessage(request.sessionId(), "user", request.input(), null, null);
        }

        // Execute via orchestrator
        AgentResponse response = orchestrator.execute(request);

        // Persist assistant message
        if (request.sessionId() != null && response.success()) {
            persistMessage(request.sessionId(), "assistant", response.output(),
                    response.agentName(), response.latencyMs());
        }

        return response;
    }

    public List<AgentInfo> listAgents() {
        return agentRegistry.getAllAgents().stream()
                .map(agent -> new AgentInfo(null, agent.getName(), agent.getDescription(), true))
                .toList();
    }

    @Transactional
    public SessionResponse createSession(SessionCreateRequest request) {
        SessionEntity session = new SessionEntity();
        session.setUserId(request.userId());
        session.setAgentName(request.agentName());
        session = sessionRepository.save(session);

        log.info("Created session '{}' for user '{}'", session.getId(), request.userId());
        return new SessionResponse(session.getId(), session.getUserId(),
                session.getAgentName(), session.getCreatedAt());
    }

    public List<MessageResponse> getSessionHistory(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        return messageRepository.findBySessionIdOrderByTimestampAsc(sessionId)
                .stream()
                .map(msg -> new MessageResponse(
                        msg.getId(), msg.getRole(), msg.getContent(),
                        msg.getAgentName(), msg.getLatencyMs(), msg.getTimestamp()))
                .toList();
    }

    public List<SessionResponse> getUserSessions(String userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(s -> new SessionResponse(s.getId(), s.getUserId(),
                        s.getAgentName(), s.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
        log.info("Deleted session '{}' and its messages", sessionId);
    }

    private void persistMessage(UUID sessionId, String role, String content,
                                String agentName, Long latencyMs) {
        MessageEntity message = new MessageEntity();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setAgentName(agentName);
        message.setLatencyMs(latencyMs);
        messageRepository.save(message);
    }
}
