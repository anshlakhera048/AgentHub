package com.agenthub.orchestrator.dag;

import java.util.*;

/**
 * Directed Acyclic Graph of agent execution nodes.
 * Validates structure on construction (no cycles, no missing deps).
 */
public class DAG {

    private final Map<String, DAGNode> nodes;

    private DAG(Map<String, DAGNode> nodes) {
        this.nodes = Collections.unmodifiableMap(nodes);
    }

    public Map<String, DAGNode> getNodes() {
        return nodes;
    }

    public DAGNode getNode(String id) {
        return nodes.get(id);
    }

    public int size() {
        return nodes.size();
    }

    /**
     * Returns the set of root nodes (no dependencies).
     */
    public Set<String> getRootNodeIds() {
        Set<String> roots = new LinkedHashSet<>();
        for (DAGNode node : nodes.values()) {
            if (node.isRoot()) {
                roots.add(node.id());
            }
        }
        return roots;
    }

    /**
     * Returns all node IDs that directly depend on the given node.
     */
    public Set<String> getDependents(String nodeId) {
        Set<String> dependents = new LinkedHashSet<>();
        for (DAGNode node : nodes.values()) {
            if (node.dependencies().contains(nodeId)) {
                dependents.add(node.id());
            }
        }
        return dependents;
    }

    /**
     * Returns a topological ordering of all node IDs.
     */
    public List<String> topologicalOrder() {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (DAGNode node : nodes.values()) {
            inDegree.putIfAbsent(node.id(), 0);
            for (String dep : node.dependencies()) {
                inDegree.merge(node.id(), 1, Integer::sum);
                inDegree.putIfAbsent(dep, 0);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);
            for (String dependent : getDependents(current)) {
                int newDegree = inDegree.merge(dependent, -1, Integer::sum);
                if (newDegree == 0) queue.add(dependent);
            }
        }
        return order;
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, DAGNode> nodes = new LinkedHashMap<>();

        public Builder addNode(DAGNode node) {
            if (nodes.containsKey(node.id())) {
                throw new IllegalArgumentException("Duplicate node id: " + node.id());
            }
            nodes.put(node.id(), node);
            return this;
        }

        public DAG build() {
            validate();
            return new DAG(new LinkedHashMap<>(nodes));
        }

        private void validate() {
            if (nodes.isEmpty()) {
                throw new IllegalArgumentException("DAG must contain at least one node");
            }

            // Check for missing dependencies
            for (DAGNode node : nodes.values()) {
                for (String dep : node.dependencies()) {
                    if (!nodes.containsKey(dep)) {
                        throw new IllegalArgumentException(
                                "Node '" + node.id() + "' depends on missing node '" + dep + "'");
                    }
                }
            }

            // Cycle detection via topological sort (Kahn's algorithm)
            Map<String, Integer> inDegree = new HashMap<>();
            for (DAGNode node : nodes.values()) {
                inDegree.putIfAbsent(node.id(), 0);
                for (String dep : node.dependencies()) {
                    inDegree.merge(node.id(), 1, Integer::sum);
                    inDegree.putIfAbsent(dep, 0);
                }
            }

            Queue<String> queue = new LinkedList<>();
            for (var entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) queue.add(entry.getKey());
            }

            int visited = 0;
            while (!queue.isEmpty()) {
                String current = queue.poll();
                visited++;
                for (DAGNode node : nodes.values()) {
                    if (node.dependencies().contains(current)) {
                        int newDegree = inDegree.merge(node.id(), -1, Integer::sum);
                        if (newDegree == 0) queue.add(node.id());
                    }
                }
            }

            if (visited != nodes.size()) {
                throw new IllegalArgumentException("DAG contains a cycle");
            }

            // Must have at least one root node
            boolean hasRoot = nodes.values().stream().anyMatch(DAGNode::isRoot);
            if (!hasRoot) {
                throw new IllegalArgumentException("DAG must have at least one root node (no dependencies)");
            }
        }
    }
}
