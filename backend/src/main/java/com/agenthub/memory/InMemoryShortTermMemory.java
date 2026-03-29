package com.agenthub.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory short-term memory fallback. Used when Redis is not available.
 * Stores conversation history in a ConcurrentHashMap — data is lost on restart.
 */
@Component
@ConditionalOnMissingBean(RedisShortTermMemory.class)
public class InMemoryShortTermMemory implements ShortTermMemory {

    private static final Logger log = LoggerFactory.getLogger(InMemoryShortTermMemory.class);

    private final Map<String, List<String>> store = new ConcurrentHashMap<>();
    private final int maxHistoryLength;

    public InMemoryShortTermMemory() {
        this(10);
    }

    public InMemoryShortTermMemory(int maxHistoryLength) {
        this.maxHistoryLength = maxHistoryLength;
        log.info("Using in-memory short-term memory (Redis not configured), maxHistory={}", maxHistoryLength);
    }

    @Override
    public void store(String sessionId, String content) {
        store.compute(sessionId, (key, entries) -> {
            if (entries == null) {
                entries = new ArrayList<>();
            }
            entries.add(content);
            if (entries.size() > maxHistoryLength) {
                entries = new ArrayList<>(
                        entries.subList(entries.size() - maxHistoryLength, entries.size()));
            }
            return entries;
        });
    }

    @Override
    public String retrieve(String sessionId) {
        List<String> entries = store.get(sessionId);
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        return String.join("\n", entries);
    }

    @Override
    public void clear(String sessionId) {
        store.remove(sessionId);
    }
}
