package com.agenthub.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, String> aliasMap = new ConcurrentHashMap<>();

    public AgentRegistry(List<Agent> agentList) {
        for (Agent agent : agentList) {
            register(agent);
        }
        log.info("AgentRegistry initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    public void register(Agent agent) {
        String canonicalName = agent.getName();
        agents.put(canonicalName, agent);
        aliasMap.put(canonicalName.toLowerCase(), canonicalName);
        aliasMap.put(toKebabCase(canonicalName), canonicalName);
        log.debug("Registered agent: {}", canonicalName);
    }

    public Optional<Agent> getAgent(String name) {
        Agent agent = agents.get(name);
        if (agent != null) return Optional.of(agent);
        String resolved = aliasMap.get(name.toLowerCase());
        if (resolved != null) {
            return Optional.ofNullable(agents.get(resolved));
        }
        return Optional.empty();
    }

    public Agent requireAgent(String name) {
        return getAgent(name).orElseThrow(() ->
                new AgentNotFoundException("Agent not found: " + name +
                        ". Available agents: " + agents.keySet()));
    }

    public Collection<Agent> getAllAgents() {
        return Collections.unmodifiableCollection(agents.values());
    }

    public Set<String> getAgentNames() {
        return Collections.unmodifiableSet(agents.keySet());
    }

    private static String toKebabCase(String pascalCase) {
        return pascalCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
