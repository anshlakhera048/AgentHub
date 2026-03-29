package com.agenthub.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void registersAndRetrievesTool() {
        Tool stub = stubTool("TestTool", "A test tool");
        ToolRegistry registry = new ToolRegistry(List.of(stub));

        assertTrue(registry.getTool("TestTool").isPresent());
        assertEquals(stub, registry.requireTool("TestTool"));
    }

    @Test
    void requireToolThrowsForMissing() {
        ToolRegistry registry = new ToolRegistry(List.of());
        assertThrows(ToolNotFoundException.class, () -> registry.requireTool("NoSuchTool"));
    }

    @Test
    void executeToolRunsAndReturnsResult() {
        Tool stub = stubTool("Adder", "Adds numbers");
        ToolRegistry registry = new ToolRegistry(List.of(stub));

        Object result = registry.executeTool("Adder", Map.of("a", 1, "b", 2));
        assertEquals("executed", result);
    }

    @Test
    void executeToolWrapsExceptions() {
        Tool failing = new Tool() {
            @Override public String getName() { return "Fail"; }
            @Override public String getDescription() { return "fails"; }
            @Override public Object execute(Map<String, Object> params) {
                throw new RuntimeException("boom");
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(failing));

        assertThrows(ToolExecutionException.class,
                () -> registry.executeTool("Fail", Map.of()));
    }

    @Test
    void getAllToolsReturnsAll() {
        ToolRegistry registry = new ToolRegistry(List.of(
                stubTool("A", "tool A"),
                stubTool("B", "tool B")
        ));
        assertEquals(2, registry.getAllTools().size());
    }

    @Test
    void getToolNamesReturnsNames() {
        ToolRegistry registry = new ToolRegistry(List.of(
                stubTool("X", "x"), stubTool("Y", "y")
        ));
        assertTrue(registry.getToolNames().contains("X"));
        assertTrue(registry.getToolNames().contains("Y"));
    }

    private static Tool stubTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public Object execute(Map<String, Object> params) { return "executed"; }
        };
    }
}
