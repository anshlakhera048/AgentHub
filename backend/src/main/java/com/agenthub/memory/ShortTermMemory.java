package com.agenthub.memory;

/**
 * Short-term memory interface backed by Redis.
 * Stores recent conversation context per session.
 */
public interface ShortTermMemory {

    void store(String sessionId, String content);

    String retrieve(String sessionId);

    void clear(String sessionId);
}
