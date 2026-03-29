package com.agenthub.orchestrator.dag;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DAGNodeTest {

    @Test
    void createValidNode() {
        DAGNode node = new DAGNode("n1", "PromptOptimizer", Set.of(), Map.of());
        assertEquals("n1", node.id());
        assertEquals("PromptOptimizer", node.agentName());
        assertTrue(node.isRoot());
    }

    @Test
    void nodeWithDependencies() {
        DAGNode node = new DAGNode("n2", "TaskPlanner", Set.of("n1"), Map.of("key", "val"));
        assertFalse(node.isRoot());
        assertEquals(Set.of("n1"), node.dependencies());
        assertEquals("val", node.parameters().get("key"));
    }

    @Test
    void nullIdRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new DAGNode(null, "Agent", Set.of(), Map.of())
        );
    }

    @Test
    void blankAgentNameRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new DAGNode("n1", "", Set.of(), Map.of())
        );
    }

    @Test
    void nullDependenciesDefaultsToEmpty() {
        DAGNode node = new DAGNode("n1", "Agent", null, null);
        assertNotNull(node.dependencies());
        assertTrue(node.dependencies().isEmpty());
        assertNotNull(node.parameters());
        assertTrue(node.parameters().isEmpty());
    }
}
