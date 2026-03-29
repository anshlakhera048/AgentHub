package com.agenthub.orchestrator.dag;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DAGTest {

    @Test
    void buildSimpleLinearDAG() {
        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "PromptOptimizer", Set.of(), Map.of()))
                .addNode(new DAGNode("b", "TaskPlanner", Set.of("a"), Map.of()))
                .build();

        assertEquals(2, dag.size());
        assertTrue(dag.getNode("a").isRoot());
        assertFalse(dag.getNode("b").isRoot());
    }

    @Test
    void buildParallelDAG() {
        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "PromptOptimizer", Set.of(), Map.of()))
                .addNode(new DAGNode("b", "TaskPlanner", Set.of(), Map.of()))
                .addNode(new DAGNode("c", "CodeReview", Set.of("a", "b"), Map.of()))
                .build();

        assertEquals(3, dag.size());
        assertEquals(Set.of("a", "b"), dag.getRootNodeIds());
        assertEquals(Set.of("c"), dag.getDependents("a"));
        assertEquals(Set.of("c"), dag.getDependents("b"));
    }

    @Test
    void topologicalOrderRespectsDependencies() {
        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "Agent1", Set.of(), Map.of()))
                .addNode(new DAGNode("b", "Agent2", Set.of("a"), Map.of()))
                .addNode(new DAGNode("c", "Agent3", Set.of("b"), Map.of()))
                .build();

        var order = dag.topologicalOrder();
        assertTrue(order.indexOf("a") < order.indexOf("b"));
        assertTrue(order.indexOf("b") < order.indexOf("c"));
    }

    @Test
    void cycleDetection() {
        assertThrows(IllegalArgumentException.class, () ->
                DAG.builder()
                        .addNode(new DAGNode("a", "Agent1", Set.of("b"), Map.of()))
                        .addNode(new DAGNode("b", "Agent2", Set.of("a"), Map.of()))
                        .build()
        );
    }

    @Test
    void missingDependencyDetection() {
        assertThrows(IllegalArgumentException.class, () ->
                DAG.builder()
                        .addNode(new DAGNode("a", "Agent1", Set.of("missing"), Map.of()))
                        .build()
        );
    }

    @Test
    void emptyDAGRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                DAG.builder().build()
        );
    }

    @Test
    void duplicateNodeIdRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                DAG.builder()
                        .addNode(new DAGNode("a", "Agent1", Set.of(), Map.of()))
                        .addNode(new DAGNode("a", "Agent2", Set.of(), Map.of()))
                        .build()
        );
    }

    @Test
    void singleNodeDAG() {
        DAG dag = DAG.builder()
                .addNode(new DAGNode("only", "PromptOptimizer", Set.of(), Map.of()))
                .build();

        assertEquals(1, dag.size());
        assertEquals(Set.of("only"), dag.getRootNodeIds());
    }

    @Test
    void diamondDAG() {
        // A -> B, A -> C, B -> D, C -> D
        DAG dag = DAG.builder()
                .addNode(new DAGNode("a", "Agent1", Set.of(), Map.of()))
                .addNode(new DAGNode("b", "Agent2", Set.of("a"), Map.of()))
                .addNode(new DAGNode("c", "Agent3", Set.of("a"), Map.of()))
                .addNode(new DAGNode("d", "Agent4", Set.of("b", "c"), Map.of()))
                .build();

        assertEquals(4, dag.size());
        var order = dag.topologicalOrder();
        assertTrue(order.indexOf("a") < order.indexOf("b"));
        assertTrue(order.indexOf("a") < order.indexOf("c"));
        assertTrue(order.indexOf("b") < order.indexOf("d"));
        assertTrue(order.indexOf("c") < order.indexOf("d"));
    }
}
