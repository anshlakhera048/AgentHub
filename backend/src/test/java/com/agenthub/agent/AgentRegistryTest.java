package com.agenthub.agent;

import com.agenthub.model.dto.AgentRequest;
import com.agenthub.model.dto.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentRegistryTest {

    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        Agent stubAgent = new Agent() {
            @Override public String getName() { return "TaskPlanner"; }
            @Override public String getDescription() { return "stub"; }
            @Override public AgentResponse execute(AgentRequest request) { return null; }
        };

        registry = new AgentRegistry(List.of(stubAgent));
    }

    @Test
    void lookupByExactName() {
        assertTrue(registry.getAgent("TaskPlanner").isPresent());
    }

    @Test
    void lookupByLowerCase() {
        assertTrue(registry.getAgent("taskplanner").isPresent());
    }

    @Test
    void lookupByKebabCase() {
        assertTrue(registry.getAgent("task-planner").isPresent());
    }

    @Test
    void lookupByUpperCase() {
        assertTrue(registry.getAgent("TASKPLANNER").isPresent());
    }

    @Test
    void lookupNonExistentReturnsEmpty() {
        assertTrue(registry.getAgent("DoesNotExist").isEmpty());
    }

    @Test
    void requireAgentThrowsForMissing() {
        assertThrows(AgentNotFoundException.class,
                () -> registry.requireAgent("Unknown"));
    }

    @Test
    void getAllAgentsReturnsRegistered() {
        assertEquals(1, registry.getAllAgents().size());
    }

    @Test
    void getAgentNamesContainsCanonicalName() {
        assertTrue(registry.getAgentNames().contains("TaskPlanner"));
    }
}
