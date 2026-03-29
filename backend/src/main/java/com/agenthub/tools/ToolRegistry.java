package com.agenthub.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            register(tool);
        }
        log.info("ToolRegistry initialized with {} tools: {}", tools.size(), tools.keySet());
    }

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
        log.debug("Registered tool: {}", tool.getName());
    }

    public Optional<Tool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Tool requireTool(String name) {
        return getTool(name).orElseThrow(() ->
                new ToolNotFoundException("Tool not found: " + name));
    }

    public Object executeTool(String name, Map<String, Object> params) {
        Tool tool = requireTool(name);
        log.info("Executing tool '{}' with params: {}", name, params.keySet());
        try {
            Object result = tool.execute(params);
            log.debug("Tool '{}' executed successfully", name);
            return result;
        } catch (Exception e) {
            log.error("Tool '{}' execution failed: {}", name, e.getMessage(), e);
            throw new ToolExecutionException("Tool '" + name + "' execution failed: " + e.getMessage(), e);
        }
    }

    public Collection<Tool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}
