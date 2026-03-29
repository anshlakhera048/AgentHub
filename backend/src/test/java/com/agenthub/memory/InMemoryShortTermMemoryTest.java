package com.agenthub.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryShortTermMemoryTest {

    private InMemoryShortTermMemory memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryShortTermMemory(3);
    }

    @Test
    void storeAndRetrieve() {
        memory.store("s1", "message 1");
        memory.store("s1", "message 2");

        String result = memory.retrieve("s1");
        assertTrue(result.contains("message 1"));
        assertTrue(result.contains("message 2"));
    }

    @Test
    void retrieveEmptySessionReturnsEmptyString() {
        assertEquals("", memory.retrieve("nonexistent"));
    }

    @Test
    void enforcesMaxHistoryLength() {
        memory.store("s1", "msg1");
        memory.store("s1", "msg2");
        memory.store("s1", "msg3");
        memory.store("s1", "msg4");

        String result = memory.retrieve("s1");
        assertFalse(result.contains("msg1"), "oldest message should be evicted");
        assertTrue(result.contains("msg2"));
        assertTrue(result.contains("msg3"));
        assertTrue(result.contains("msg4"));
    }

    @Test
    void clearRemovesSession() {
        memory.store("s1", "data");
        memory.clear("s1");

        assertEquals("", memory.retrieve("s1"));
    }

    @Test
    void sessionsAreIsolated() {
        memory.store("s1", "session 1 data");
        memory.store("s2", "session 2 data");

        assertTrue(memory.retrieve("s1").contains("session 1 data"));
        assertFalse(memory.retrieve("s1").contains("session 2 data"));
        assertTrue(memory.retrieve("s2").contains("session 2 data"));
    }
}
