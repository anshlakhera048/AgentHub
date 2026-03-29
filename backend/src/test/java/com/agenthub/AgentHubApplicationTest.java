package com.agenthub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AgentHubApplicationTest {

    @Test
    void mainClassLoads() {
        assertDoesNotThrow(() -> Class.forName("com.agenthub.AgentHubApplication"));
    }
}
